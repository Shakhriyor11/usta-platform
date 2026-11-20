// Usta platformasi — yuklama testi (k6).
// Maqsad (tasdiqlangan): ~1000 bir vaqtdagi foydalanuvchi, kuniga 5000 buyurtma, server 4 vCPU / 8 GB.
//   1000 foydalanuvchi × har ~7 s da bitta so'rov ≈ 140 RPS o'qish + ustalar heartbeat'i + buyurtmalar.
//   Buyurtmalar: kunlik 5000 -> eng band soat ~750/soat ≈ 0.2/s; test 1/s (5× zaxira) to'liq hayot sikli bilan.
// Ishga tushirish: loadtest/run.sh (PROFILE=load | stress | smoke)
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://api:8080';
const SECRET = __ENV.JWT_SECRET;
const PROFILE = __ENV.PROFILE || 'load';
const CUSTOMERS = 2000;
const MASTERS = 500;
const SERVICES = JSON.parse(__ENV.SERVICES); // {faucet, pipe, toilet, plumbing}

const orderFlow = new Trend('order_flow_duration', true);
const ordersDone = new Counter('orders_completed');

// ---------------------------------------------------------------- profillar
const profiles = {
  smoke: {
    browse: { executor: 'constant-arrival-rate', rate: 10, timeUnit: '1s', duration: '30s', preAllocatedVUs: 20 },
    orders: { executor: 'constant-arrival-rate', rate: 1, timeUnit: '2s', duration: '30s', preAllocatedVUs: 5, exec: 'orders' },
  },
  load: {
    browse: { executor: 'constant-arrival-rate', rate: 140, timeUnit: '1s', duration: __ENV.DURATION || '5m', preAllocatedVUs: 200, maxVUs: 1000 },
    presence: { executor: 'constant-arrival-rate', rate: 17, timeUnit: '1s', duration: __ENV.DURATION || '5m', preAllocatedVUs: 30, exec: 'presence' },
    orders: { executor: 'constant-arrival-rate', rate: 1, timeUnit: '1s', duration: __ENV.DURATION || '5m', preAllocatedVUs: 20, maxVUs: 100, exec: 'orders' },
  },
  stress: {
    browse: {
      executor: 'ramping-arrival-rate', startRate: 100, timeUnit: '1s', preAllocatedVUs: 300, maxVUs: 3000,
      stages: [{ target: 300, duration: '2m' }, { target: 600, duration: '2m' }, { target: 900, duration: '2m' },
               { target: 1200, duration: '2m' }],
    },
    orders: { executor: 'constant-arrival-rate', rate: 2, timeUnit: '1s', duration: '8m', preAllocatedVUs: 30, maxVUs: 200, exec: 'orders' },
  },
};

export const options = {
  scenarios: Object.fromEntries(Object.entries(profiles[PROFILE]).map(([k, v]) => [k, { exec: 'browse', ...v }])),
  thresholds: PROFILE === 'stress' ? {} : {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{kind:read}': ['p(95)<300', 'p(99)<800'],
    'http_req_duration{kind:write}': ['p(95)<500'],
    order_flow_duration: ['p(95)<3000'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ---------------------------------------------------------------- yordamchilar
function md5Uuid(s) {
  const h = crypto.md5(s, 'hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}
const b64 = (o) => encoding.b64encode(JSON.stringify(o), 'rawurl');
function jwt(sub, phone, roles) {
  const now = Math.floor(Date.now() / 1000);
  const head = b64({ alg: 'HS256', typ: 'JWT' });
  const body = b64({ iss: 'usta-platform', sub, iat: now, exp: now + 3600, typ: 'access', phone, roles, jti: md5Uuid(sub + now + Math.random()) });
  const sig = crypto.hmac('sha256', SECRET, `${head}.${body}`, 'base64rawurl');
  return `${head}.${body}.${sig}`;
}
const tokenCache = {};
function customer(i) {
  const id = md5Uuid(`lt-customer-${i}`);
  return tokenCache[id] ||= { id, token: jwt(id, '+99877' + String(i).padStart(7, '0'), ['CUSTOMER']) };
}
function master(i) {
  const id = md5Uuid(`lt-master-${i}`);
  return tokenCache[id] ||= { id, token: jwt(id, '+99877' + String(5000000 + i).padStart(7, '0'), ['MASTER']) };
}
const rnd = (n) => 1 + Math.floor(Math.random() * n);
const headers = (u, extra = {}) => ({ Authorization: `Bearer ${u.token}`, 'Content-Type': 'application/json', 'Accept-Language': 'uz', ...extra });
// mahalla markazlari (kesh foydali bo'lishi uchun) + tasodifiy nuqtalar
const GRID = Array.from({ length: 150 }, (_, k) => [41.25 + (k % 15) * 0.009, 69.17 + Math.floor(k / 15) * 0.018]);
function point() {
  if (Math.random() < 0.6) return GRID[Math.floor(Math.random() * GRID.length)];
  return [41.24 + Math.random() * 0.14, 69.16 + Math.random() * 0.2];
}

// ---------------------------------------------------------------- ssenariylar
export function browse() {
  const u = customer(rnd(CUSTOMERS));
  const r = Math.random();
  const p = { headers: headers(u), tags: { kind: 'read' } };
  let res;
  if (r < 0.45) {
    const [lat, lng] = point();
    const svc = Math.random() < 0.7 ? `serviceId=${SERVICES.faucet}` : `categoryId=${SERVICES.plumbing}`;
    const sort = ['RATING', 'RATING', 'DISTANCE', 'PRICE'][Math.floor(Math.random() * 4)];
    res = http.get(`${BASE}/api/v1/search/masters?${svc}&lat=${lat.toFixed(5)}&lng=${lng.toFixed(5)}&sort=${sort}&size=20`,
      { ...p, tags: { kind: 'read', name: 'search' } });
  } else if (r < 0.55) {
    res = http.get(`${BASE}/api/v1/public/catalog/categories`, { ...p, tags: { kind: 'read', name: 'catalog' } });
  } else if (r < 0.70) {
    res = http.get(`${BASE}/api/v1/masters/${master(rnd(MASTERS)).id}`, { ...p, tags: { kind: 'read', name: 'master-card' } });
  } else if (r < 0.80) {
    res = http.get(`${BASE}/api/v1/masters/${master(rnd(MASTERS)).id}/services`, { ...p, tags: { kind: 'read', name: 'master-services' } });
  } else if (r < 0.85) {
    res = http.get(`${BASE}/api/v1/masters/${master(rnd(MASTERS)).id}/reviews`, { ...p, tags: { kind: 'read', name: 'reviews' } });
  } else if (r < 0.93) {
    res = http.get(`${BASE}/api/v1/notifications/unread-count`, { ...p, tags: { kind: 'read', name: 'unread' } });
  } else {
    res = http.get(`${BASE}/api/v1/customer/orders?size=10`, { ...p, tags: { kind: 'read', name: 'my-orders' } });
  }
  check(res, { 'read 200': (x) => x.status === 200 });
}

export function presence() {
  const m = master(rnd(MASTERS));
  const res = http.post(`${BASE}/api/v1/master/presence/heartbeat`, null, { headers: headers(m), tags: { kind: 'write', name: 'heartbeat' } });
  check(res, { 'heartbeat 204': (x) => x.status === 204 });
}

/** To'liq buyurtma: yaratish -> taklif -> qabul (lock) -> chat -> boshlash -> yakunlash (komissiya) -> baho. */
export function orders() {
  const c = customer(rnd(CUSTOMERS));
  const mi = rnd(MASTERS);
  const m = master(mi);
  const t0 = Date.now();
  group('order-flow', () => {
    // ustaning ish hududi ichidagi nuqta: master_profiles'dan emas — seed'da ustalar Toshkent bo'ylab, radius >= 8 km;
    // preferredMasterId mos kelmasa (409) — oddiy taqsimlashga qoldiramiz
    const card = http.get(`${BASE}/api/v1/masters/${m.id}`, { headers: headers(c), tags: { kind: 'read', name: 'master-card' } });
    if (card.status !== 200) return;
    const [lat, lng] = point();
    const w = { headers: headers(c), tags: { kind: 'write' } };
    let res = http.post(`${BASE}/api/v1/customer/orders`, JSON.stringify({
      serviceId: SERVICES.faucet, description: 'Yuklama testi: jo\'mrak almashtirish', preferredMasterId: m.id,
      address: { address: 'Toshkent', details: '1-xonadon', location: { latitude: lat, longitude: lng } },
    }), { ...w, tags: { kind: 'write', name: 'order-create' }, responseCallback: http.expectedStatuses(201, 409) });
    if (res.status === 409) { // nuqta tanlangan ustaning ish hududidan tashqarida — kutilgan javob
      res = http.post(`${BASE}/api/v1/customer/orders`, JSON.stringify({
        serviceId: SERVICES.faucet, description: 'Yuklama testi', address: { address: 'Toshkent', location: { latitude: lat, longitude: lng } },
      }), { ...w, tags: { kind: 'write', name: 'order-create' } });
      check(res, { 'order created': (x) => x.status === 201 });
      return; // taklif qaysi ustaga borgani noma'lum — oqim shu yerda tugaydi (taqsimlash yuklamasi baribir sinaldi)
    }
    if (!check(res, { 'order created': (x) => x.status === 201 })) return;
    const orderId = res.json('id');
    const mw = { headers: headers(m), tags: { kind: 'write' } };
    res = http.post(`${BASE}/api/v1/master/offers/${orderId}/accept`, null, { ...mw, tags: { kind: 'write', name: 'accept' } });
    if (!check(res, { 'accepted': (x) => x.status === 200 })) return;
    res = http.post(`${BASE}/api/v1/orders/${orderId}/chat/messages`, JSON.stringify({ content: 'Salom, qachon kelasiz?' }), { ...w, tags: { kind: 'write', name: 'chat' } });
    check(res, { 'chat sent': (x) => x.status === 201 });
    res = http.post(`${BASE}/api/v1/orders/${orderId}/chat/messages`, JSON.stringify({ content: '30 daqiqada boraman' }), { ...mw, tags: { kind: 'write', name: 'chat' } });
    check(res, { 'chat sent': (x) => x.status === 201 });
    res = http.post(`${BASE}/api/v1/master/orders/${orderId}/start`, null, { ...mw, tags: { kind: 'write', name: 'start' } });
    check(res, { 'started': (x) => x.status === 200 });
    res = http.post(`${BASE}/api/v1/master/orders/${orderId}/complete`, JSON.stringify({ price: 150000 }), { ...mw, tags: { kind: 'write', name: 'complete' } });
    check(res, { 'completed': (x) => x.status === 200 && x.json('status') === 'COMMISSION' });
    res = http.post(`${BASE}/api/v1/customer/orders/${orderId}/review`, JSON.stringify({ rating: 4 + Math.round(Math.random()), comment: 'Yaxshi' }), { ...w, tags: { kind: 'write', name: 'review' } });
    if (check(res, { 'reviewed': (x) => x.status === 201 })) {
      ordersDone.add(1);
      orderFlow.add(Date.now() - t0);
    }
  });
}
