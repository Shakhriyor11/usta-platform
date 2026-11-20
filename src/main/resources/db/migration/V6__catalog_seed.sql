-- =====================================================================
-- V6: Boshlang'ich katalog. ID'lar slug'dan deterministik (md5 -> uuid), shuning uchun
-- har muhitda bir xil. Keyingi o'zgarishlar admin API orqali.
-- =====================================================================
INSERT INTO categories (id, parent_id, slug, name_uz, name_ru, name_en, icon, sort_order) VALUES
    (md5('cat:plumbing')::uuid,   NULL, 'plumbing',   'Santexnika',         'Сантехника',          'Plumbing',          'plumbing',   10),
    (md5('cat:electrical')::uuid, NULL, 'electrical', 'Elektrika',          'Электрика',           'Electrical',        'electrical', 20),
    (md5('cat:repair')::uuid,     NULL, 'repair',     'Taʼmirlash',         'Ремонт',              'Repair',            'repair',     30),
    (md5('cat:appliances')::uuid, NULL, 'appliances', 'Maishiy texnika',    'Бытовая техника',     'Home appliances',   'appliances', 40),
    (md5('cat:climate')::uuid,    NULL, 'climate',    'Konditsioner va isitish', 'Кондиционеры и отопление', 'HVAC',  'climate',    50),
    (md5('cat:furniture')::uuid,  NULL, 'furniture',  'Mebel',              'Мебель',              'Furniture',         'furniture',  60);

INSERT INTO categories (id, parent_id, slug, name_uz, name_ru, name_en, sort_order) VALUES
    (md5('cat:repair-painting')::uuid, md5('cat:repair')::uuid, 'repair-painting', 'Boʻyoq va devor ishlari', 'Малярные работы', 'Painting', 10),
    (md5('cat:repair-tiling')::uuid,   md5('cat:repair')::uuid, 'repair-tiling',   'Kafel yotqizish',        'Укладка плитки',  'Tiling',   20);

INSERT INTO services (id, category_id, slug, name_uz, name_ru, name_en, sort_order) VALUES
    (md5('svc:faucet-install')::uuid,     md5('cat:plumbing')::uuid,        'faucet-install',     'Joʻmrak oʻrnatish',          'Установка смесителя',          'Faucet installation',      10),
    (md5('svc:pipe-leak')::uuid,          md5('cat:plumbing')::uuid,        'pipe-leak',          'Quvur oqishini tuzatish',    'Устранение протечки',          'Pipe leak repair',         20),
    (md5('svc:toilet-install')::uuid,     md5('cat:plumbing')::uuid,        'toilet-install',     'Unitaz oʻrnatish',           'Установка унитаза',            'Toilet installation',      30),
    (md5('svc:drain-cleaning')::uuid,     md5('cat:plumbing')::uuid,        'drain-cleaning',     'Kanalizatsiyani tozalash',   'Прочистка канализации',        'Drain cleaning',           40),
    (md5('svc:socket-install')::uuid,     md5('cat:electrical')::uuid,      'socket-install',     'Rozetka/viklyuchatel oʻrnatish', 'Установка розеток/выключателей', 'Socket/switch installation', 10),
    (md5('svc:wiring')::uuid,             md5('cat:electrical')::uuid,      'wiring',             'Elektr simlarini tortish',   'Электропроводка',              'Wiring',                   20),
    (md5('svc:chandelier-install')::uuid, md5('cat:electrical')::uuid,      'chandelier-install', 'Qandil oʻrnatish',           'Установка люстры',             'Chandelier installation',  30),
    (md5('svc:wall-painting')::uuid,      md5('cat:repair-painting')::uuid, 'wall-painting',      'Devor boʻyash',              'Покраска стен',                'Wall painting',            10),
    (md5('svc:wallpaper')::uuid,          md5('cat:repair-painting')::uuid, 'wallpaper',          'Gulqogʻoz yopishtirish',     'Поклейка обоев',               'Wallpapering',             20),
    (md5('svc:tile-laying')::uuid,        md5('cat:repair-tiling')::uuid,   'tile-laying',        'Kafel yotqizish',            'Укладка плитки',               'Tile laying',              10),
    (md5('svc:washer-repair')::uuid,      md5('cat:appliances')::uuid,      'washer-repair',      'Kir yuvish mashinasini taʼmirlash', 'Ремонт стиральных машин', 'Washing machine repair', 10),
    (md5('svc:fridge-repair')::uuid,      md5('cat:appliances')::uuid,      'fridge-repair',      'Muzlatgich taʼmirlash',      'Ремонт холодильников',         'Refrigerator repair',      20),
    (md5('svc:ac-install')::uuid,         md5('cat:climate')::uuid,         'ac-install',         'Konditsioner oʻrnatish',     'Установка кондиционера',       'AC installation',          10),
    (md5('svc:ac-service')::uuid,         md5('cat:climate')::uuid,         'ac-service',         'Konditsionerga xizmat koʻrsatish', 'Обслуживание кондиционера', 'AC servicing',          20),
    (md5('svc:furniture-assembly')::uuid, md5('cat:furniture')::uuid,       'furniture-assembly', 'Mebel yigʻish',              'Сборка мебели',                'Furniture assembly',       10);
