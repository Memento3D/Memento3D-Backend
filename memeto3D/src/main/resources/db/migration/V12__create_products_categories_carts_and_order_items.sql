-- V12: Product Categories, Products, Carts, Order Items, Cash Payment Support

-- 1. Product Categories
CREATE TABLE IF NOT EXISTS public.product_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    image_url VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_product_categories_slug ON public.product_categories (slug);
CREATE INDEX IF NOT EXISTS idx_product_categories_active ON public.product_categories (active);

-- 2. Products
CREATE TABLE IF NOT EXISTS public.products (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES public.product_categories(id) ON DELETE RESTRICT,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    short_description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    thumbnail_url VARCHAR(500),
    images TEXT, -- JSON array of image URLs
    active BOOLEAN NOT NULL DEFAULT TRUE,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    availability VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_products_category ON public.products (category_id);
CREATE INDEX IF NOT EXISTS idx_products_slug ON public.products (slug);
CREATE INDEX IF NOT EXISTS idx_products_active ON public.products (active);
CREATE INDEX IF NOT EXISTS idx_products_featured ON public.products (featured);

-- 3. Shopping Carts
CREATE TABLE IF NOT EXISTS public.carts (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cart_customer UNIQUE (customer_id)
);

CREATE INDEX IF NOT EXISTS idx_carts_customer ON public.carts (customer_id);

-- 4. Cart Items
CREATE TABLE IF NOT EXISTS public.cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES public.carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES public.products(id) ON DELETE CASCADE,
    quantity INT NOT NULL DEFAULT 1,
    unit_price NUMERIC(12, 2) NOT NULL,
    customization_data TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cart_items_cart ON public.cart_items (cart_id);
CREATE INDEX IF NOT EXISTS idx_cart_items_product ON public.cart_items (product_id);

-- 5. Extend print_orders to support full e-commerce checkout & code
ALTER TABLE public.print_orders ADD COLUMN IF NOT EXISTS order_code VARCHAR(100);
ALTER TABLE public.print_orders ADD COLUMN IF NOT EXISTS subtotal NUMERIC(12, 2) DEFAULT 0;
ALTER TABLE public.print_orders ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12, 2) DEFAULT 0;
ALTER TABLE public.print_orders ADD COLUMN IF NOT EXISTS shipping_fee NUMERIC(12, 2) DEFAULT 0;
ALTER TABLE public.print_orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(30);
ALTER TABLE public.print_orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(30) DEFAULT 'PENDING';
ALTER TABLE public.print_orders ADD COLUMN IF NOT EXISTS note TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_print_orders_order_code ON public.print_orders (order_code) WHERE order_code IS NOT NULL;

-- 6. Order Items (Snapshot of Products)
CREATE TABLE IF NOT EXISTS public.order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES public.print_orders(id) ON DELETE CASCADE,
    product_id UUID REFERENCES public.products(id) ON DELETE SET NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    subtotal NUMERIC(12, 2) NOT NULL,
    customization_snapshot TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_order_items_order ON public.order_items (order_id);

-- 7. Extend payment_transactions for CASH confirmation
ALTER TABLE public.payment_transactions ADD COLUMN IF NOT EXISTS confirmed_by UUID REFERENCES public.users(id) ON DELETE SET NULL;

-- 8. Seed Default Product Categories
INSERT INTO public.product_categories (id, name, slug, description, image_url, active, display_order, created_at, updated_at)
VALUES
    ('c1111111-1111-1111-1111-111111111111', 'Mini Keepsake', 'mini-keepsake', 'Chế tác phù điêu 3D bỏ túi, móc khóa bền chắc bằng nhựa quang hóa cao cấp.', 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500', true, 1, NOW(), NOW()),
    ('c2222222-2222-2222-2222-222222222222', 'Standard Keepsake', 'standard-keepsake', 'Tượng chân dung desk sculpture chi tiết cao kèm đế gỗ tự nhiên.', 'https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=500', true, 2, NOW(), NOW()),
    ('c3333333-3333-3333-3333-333333333333', 'Premium Keepsake', 'premium-keepsake', 'Tác phẩm Signature Memorial tỉ lệ bảo tàng, xử lý bề mặt thủ công tinh xảo.', 'https://images.unsplash.com/photo-1544717305-2782549b5136?w=500', true, 3, NOW(), NOW()),
    ('c4444444-4444-4444-4444-444444444444', 'Keychain & Charm', 'keychain-charm', 'Móc khóa 3D mini chân dung cá nhân, phụ kiện balo, chìa khóa độc bản.', 'https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=500', true, 4, NOW(), NOW()),
    ('c5555555-5555-5555-5555-555555555555', 'Figurine & Bust', 'figurine-bust', 'Tượng bán thân và chibi 3D tái hiện sống động dáng hình và biểu cảm.', 'https://images.unsplash.com/photo-1563245372-f21724e3856d?w=500', true, 5, NOW(), NOW()),
    ('c6666666-6666-6666-6666-666666666666', 'Graduation Gift', 'graduation-gift', 'Quà tặng lễ tốt nghiệp 3D lưu giữ khoảnh khắc cử nhân vinh danh.', 'https://images.unsplash.com/photo-1523050854058-8df90110c9f1?w=500', true, 6, NOW(), NOW()),
    ('c7777777-7777-7777-7777-777777777777', 'Couple Gift', 'couple-gift', 'Kỷ vật tình yêu 3D đôi bạn, quà cưới và kỷ niệm ngày yêu.', 'https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=500', true, 7, NOW(), NOW()),
    ('c8888888-8888-8888-8888-888888888888', 'Family Gift', 'family-gift', 'Tác phẩm sum vầy gia đình 3D gắn kết thế hệ và kỷ niệm ấm cúng.', 'https://images.unsplash.com/photo-1511895426328-dc8714191300?w=500', true, 8, NOW(), NOW())
ON CONFLICT (slug) DO NOTHING;

-- 9. Seed Default Products (16 Products across all 8 categories)
INSERT INTO public.products (id, category_id, name, slug, description, short_description, price, thumbnail_url, images, active, featured, availability, created_at, updated_at)
VALUES
    -- Category 1: Mini Keepsake
    (
        'd1111111-1111-1111-1111-111111111111',
        'c1111111-1111-1111-1111-111111111111',
        'Mini Keepsake Charm (5cm)',
        'mini-keepsake-charm-5cm',
        'Móc khóa phù điêu 3D bỏ túi độ phân giải cao, đúc từ photopolymer resin siêu bền, chống va đập và chống trầy xước. Phù hợp làm quà tặng bạn bè, người thân hoặc kỷ vật mang theo bên người.',
        'Móc khóa 3D bỏ túi bền chắc, độ chi tiết siêu nét.',
        150000,
        'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=600',
        '["https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800","https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=800"]',
        true,
        true,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    (
        'd1222222-1111-1111-1111-111111111111',
        'c1111111-1111-1111-1111-111111111111',
        'Pocket Memorial Lithophane 3D (6cm)',
        'pocket-memorial-lithophane-3d-6cm',
        'Tranh thấu quang Lithophane 3D dạng thẻ bỏ túi. Hình ảnh chân dung nổi khối hiện lên sống động đầy cảm xúc khi có ánh sáng chiếu qua từ đèn flash điện thoại.',
        'Tranh thấu quang 3D phát sáng kỳ diệu khi chiếu đèn.',
        180000,
        'https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=600',
        '["https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=800"]',
        true,
        false,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    -- Category 2: Standard Keepsake
    (
        'd2111111-2222-2222-2222-222222222222',
        'c2222222-2222-2222-2222-222222222222',
        'Tượng Chân Dung Desk Sculpture (15cm)',
        'tuong-chan-dung-desk-sculpture-15cm',
        'Định dạng tượng kỷ niệm phổ biến và được yêu thích nhất tại Memento3D. Chi tiết đường nét khuôn mặt được dựng hình sắc nét từ ảnh chụp bằng AI neural photogrammetry, hoàn thiện kèm đế gỗ sồi tự nhiên sang trọng.',
        'Tượng chân dung để bàn kèm đế gỗ cao cấp, kích thước 15cm.',
        350000,
        'https://images.unsplash.com/photo-1544717305-2782549b5136?w=600',
        '["https://images.unsplash.com/photo-1544717305-2782549b5136?w=800","https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=800"]',
        true,
        true,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    (
        'd2222222-2222-2222-2222-222222222222',
        'c2222222-2222-2222-2222-222222222222',
        'Phù Điêu Chân Dung Nổi Bàn Làm Việc (14cm)',
        'phu-dieu-chan-dung-noi-ban-lam-viec-14cm',
        'Phù điêu bán diện 3D nổi khối nghệ thuật, sắc nét từng góc cạnh ánh sáng. Thích hợp trang trí bàn làm việc, kệ sách cá nhân.',
        'Phù điêu 3D nổi khối góc nhìn nghệ thuật hiện đại.',
        390000,
        'https://images.unsplash.com/photo-1578328819058-b69f3a3b0f6b?w=600',
        '["https://images.unsplash.com/photo-1578328819058-b69f3a3b0f6b?w=800"]',
        true,
        false,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    -- Category 3: Premium Keepsake
    (
        'd3111111-3333-3333-3333-333333333333',
        'c3333333-3333-3333-3333-333333333333',
        'Tác Phẩm Signature Memorial (22cm)',
        'tac-pham-signature-memorial-22cm',
        'Tượng trưng bày kích thước lớn đạt tiêu chuẩn bảo tàng nghệ thuật. Sử dụng nhựa resin quang hóa cao cấp phủ patina đồng/kim loại cổ điển, khắc tên và lời đề tặng bằng laser trên bảng đồng.',
        'Tượng tỷ lệ bảo tàng 22cm, hoàn thiện patina thủ công độc bản.',
        750000,
        'https://images.unsplash.com/photo-1569173112611-52a7cd38bea9?w=600',
        '["https://images.unsplash.com/photo-1569173112611-52a7cd38bea9?w=800","https://images.unsplash.com/photo-1554188248-986adbb73be4?w=800"]',
        true,
        true,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    (
        'd3222222-3333-3333-3333-333333333333',
        'c3333333-3333-3333-3333-333333333333',
        'Imperial Bust Sculpture Nghệ Thuật (25cm)',
        'imperial-bust-sculpture-nghe-thuat-25cm',
        'Phiên bản tượng bán thân cao cấp nhất, tỷ lệ 1:6 hoàn thiện nhám mờ sang trọng với lớp phủ bảo vệ nano chống UV vĩnh cửu.',
        'Tượng bán thân 25cm dát viền vàng nghệ thuật đỉnh cao.',
        950000,
        'https://images.unsplash.com/photo-1554188248-986adbb73be4?w=600',
        '["https://images.unsplash.com/photo-1554188248-986adbb73be4?w=800"]',
        true,
        true,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    -- Category 4: Keychain & Charm
    (
        'd4111111-4444-4444-4444-444444444444',
        'c4444444-4444-4444-4444-444444444444',
        'Móc Khóa Balo Mini Avatar 3D (4.5cm)',
        'moc-khoa-balo-mini-avatar-3d-4-5cm',
        'Móc khóa cá nhân hóa tạo hình avatar 3D ngộ nghĩnh, móc kim loại titanium chống gỉ sét, kèm chuông và dây da bền bỉ.',
        'Móc khóa avatar 3D mini kèm khoen titanium siêu bền.',
        120000,
        'https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=600',
        '["https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=800"]',
        true,
        true,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    (
        'd4222222-4444-4444-4444-444444444444',
        'c4444444-4444-4444-4444-444444444444',
        'Charm Bỏ Túi Mascot Kỷ Niệm (5cm)',
        'charm-bo-tui-mascot-ky-niem-5cm',
        'Charm thú cưng hoặc linh vật biểu trưng 3D đúc nguyên khối, mạ màu pastel dịu mắt, thích hợp gắn chìa khóa xe hoặc túi xách.',
        'Charm linh vật biểu trưng đúc nguyên khối pastel cao cấp.',
        135000,
        'https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600',
        '["https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=800"]',
        true,
        false,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    -- Category 5: Figurine & Bust
    (
        'd5111111-5555-5555-5555-555555555555',
        'c5555555-5555-5555-5555-555555555555',
        'Tượng Bán Thân Chibi Nghệ Thuật (12cm)',
        'tuong-ban-than-chibi-nghe-thuat-12cm',
        'Phong cách chibi hóa chân dung đáng yêu nhưng vẫn giữ trọn vẹn đặc điểm nhận dạng khuôn mặt và nụ cười của bạn.',
        'Tượng chibi 3D tái hiện biểu cảm sinh động, đáng yêu.',
        290000,
        'https://images.unsplash.com/photo-1563245372-f21724e3856d?w=600',
        '["https://images.unsplash.com/photo-1563245372-f21724e3856d?w=800"]',
        true,
        true,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    (
        'd5222222-5555-5555-5555-555555555555',
        'c5555555-5555-5555-5555-555555555555',
        'Tượng Heroic Pose Cá Nhân Hóa (18cm)',
        'tuong-heroic-pose-ca-nhan-hoa-18cm',
        'Biến hóa bạn thành nhân vật siêu anh hùng hoặc chiến binh huyền thoại với dáng đứng oai phong theo mô hình 3D tỉ lệ chuẩn.',
        'Tượng phong cách anh hùng cá nhân hóa tư thế mạnh mẽ.',
        480000,
        'https://images.unsplash.com/photo-1608889825205-eebdb9fc5806?w=600',
        '["https://images.unsplash.com/photo-1608889825205-eebdb9fc5806?w=800"]',
        true,
        false,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    -- Category 6: Graduation Gift
    (
        'd6111111-6666-6666-6666-666666666666',
        'c6666666-6666-6666-6666-666666666666',
        'Kỷ Niệm Tốt Nghiệp Graduation Cap 3D (16cm)',
        'ky-niem-tot-nghiep-graduation-cap-3d',
        'Tượng cử nhân 3D mặc lễ phục tốt nghiệp, tay cầm bằng và mũ cử nhân được cá nhân hóa khuôn mặt người nhận. Món quà ý nghĩa vinh danh ngày tốt nghiệp đại học.',
        'Tượng cử nhân lưu niệm tốt nghiệp vinh danh ngày trọng đại.',
        420000,
        'https://images.unsplash.com/photo-1523050854058-8df90110c9f1?w=600',
        '["https://images.unsplash.com/photo-1523050854058-8df90110c9f1?w=800"]',
        true,
        false,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    (
        'd6222222-6666-6666-6666-666666666666',
        'c6666666-6666-6666-6666-666666666666',
        'Bộ Tượng Cử Nhân Danh Dự Honor Graduate (18cm)',
        'bo-tuong-cu-nhan-danh-du-honor-graduate-18cm',
        'Phiên bản cử nhân danh dự kèm bục vinh danh khắc tên trường, niên khóa và lời chúc mừng từ gia đình, thầy cô.',
        'Tượng cử nhân kèm bục vinh danh khắc tên trường sang trọng.',
        490000,
        'https://images.unsplash.com/photo-1627556704290-2b1f5853ff78?w=600',
        '["https://images.unsplash.com/photo-1627556704290-2b1f5853ff78?w=800"]',
        true,
        true,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    -- Category 7: Couple Gift
    (
        'd7111111-7777-7777-7777-777777777777',
        'c7777777-7777-7777-7777-777777777777',
        'Tượng Đôi Uyên Ương Couple Love 3D (16cm)',
        'tuong-doi-uyen-uong-couple-love-3d',
        'Tác phẩm điêu khắc đôi 3D tái hiện khoảnh khắc lãng mạn của hai bạn từ ảnh chụp. Món quà tuyệt vời cho dịp kỷ niệm ngày yêu, ngày cưới hay Valentine.',
        'Tượng cặp đôi 3D cá nhân hóa kỷ niệm tình yêu vĩnh cửu.',
        520000,
        'https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=600',
        '["https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=800"]',
        true,
        true,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    (
        'd7222222-7777-7777-7777-777777777777',
        'c7777777-7777-7777-7777-777777777777',
        'Khung Tranh Phù Điêu Nhẫn Cưới Wedding Vow 3D',
        'khung-tranh-phu-dieu-nhan-cuoi-wedding-vow-3d',
        'Khung tranh hộp kính 3D kết hợp đôi nhẫn đúc nổi và chân dung cô dâu chú rể ngày trọng đại, lưu giữ lời thề nguyền trọn đời.',
        'Khung tranh phù điêu 3D đám cưới lưu giữ khoảnh khắc thiêng liêng.',
        650000,
        'https://images.unsplash.com/photo-1515934751635-c81c6bc9a2d8?w=600',
        '["https://images.unsplash.com/photo-1515934751635-c81c6bc9a2d8?w=800"]',
        true,
        false,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    -- Category 8: Family Gift
    (
        'd8111111-8888-8888-8888-888888888888',
        'c8888888-8888-8888-8888-888888888888',
        'Tượng Sum Vầy Gia Đình Family Forever 3D (20cm)',
        'tuong-sum-vay-gia-dinh-family-forever-3d',
        'Bức tranh phù điêu 3D gia đình đa thế hệ, lưu giữ trọn vẹn nụ cười và khoảnh khắc đoàn tụ ấm áp. Đế gỗ óc chó cao cấp khắc gia huy hoặc tên gia đình.',
        'Phù điêu 3D gia đình lưu giữ khoảnh khắc sum họp ấm cúng.',
        890000,
        'https://images.unsplash.com/photo-1511895426328-dc8714191300?w=600',
        '["https://images.unsplash.com/photo-1511895426328-dc8714191300?w=800"]',
        true,
        true,
        'AVAILABLE',
        NOW(),
        NOW()
    ),
    (
        'd8222222-8888-8888-8888-888888888888',
        'c8888888-8888-8888-8888-888888888888',
        'Cây Gia Phả Phù Điêu Thế Hệ Multi-Gen Heritage (24cm)',
        'cay-gia-pha-phu-dieu-the-he-multi-gen-heritage-24cm',
        'Kiến trúc điêu khắc cây gia phả 3D với từng nhánh gắn kết hình ảnh nổi các thế hệ ông bà, cha mẹ và con cháu, là báu vật truyền đời.',
        'Tác phẩm cây gia phả 3D kết nối truyền thống gia đình.',
        1150000,
        'https://images.unsplash.com/photo-1581579438747-1dc8d17bbce4?w=600',
        '["https://images.unsplash.com/photo-1581579438747-1dc8d17bbce4?w=800"]',
        true,
        false,
        'AVAILABLE',
        NOW(),
        NOW()
    )
ON CONFLICT (slug) DO NOTHING;

