// Seed'dan keyin qidiruv indeksini qayta qurish (admin tokeni bilan).
import http from 'k6/http';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

export const options = { vus: 1, iterations: 1 };
export default function () {
  const now = Math.floor(Date.now() / 1000);
  const b64 = (o) => encoding.b64encode(JSON.stringify(o), 'rawurl');
  const head = b64({ alg: 'HS256', typ: 'JWT' });
  const body = b64({ iss: 'usta-platform', sub: __ENV.ADMIN_ID, iat: now, exp: now + 600, typ: 'access', phone: '+998990000001', roles: ['SUPER_ADMIN'] });
  const token = `${head}.${body}.${crypto.hmac('sha256', __ENV.JWT_SECRET, `${head}.${body}`, 'base64rawurl')}`;
  const res = http.post('http://api:8080/api/v1/admin/search/reindex', null, { headers: { Authorization: `Bearer ${token}` }, timeout: '300s' });
  console.log(`reindex: ${res.status} ${res.body}`);
}
