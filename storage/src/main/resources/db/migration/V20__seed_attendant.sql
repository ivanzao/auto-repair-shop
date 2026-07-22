INSERT INTO attendants (id, name, document, email, contact)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Admin',
    '52998224725',
    'admin@auto-repair-shop.com',
    '11999999999'
)
ON CONFLICT (id) DO NOTHING;
