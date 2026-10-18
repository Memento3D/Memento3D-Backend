package com.memento3d.shared.config;

import com.memento3d.cart.domain.model.Cart;
import com.memento3d.cart.domain.model.CartItem;
import com.memento3d.cart.infrastructure.persistence.repository.CartRepository;
import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.inventory.api.request.CreateStockReceiptRequest;
import com.memento3d.inventory.application.service.InventoryService;
import com.memento3d.material.api.request.CreateMaterialRequest;
import com.memento3d.material.api.response.MaterialResponse;
import com.memento3d.material.application.service.MaterialService;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.domain.model.MaterialType;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderItem;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.printers.domain.model.Printer;
import com.memento3d.printers.domain.model.PrinterProvider;
import com.memento3d.printers.domain.model.PrinterStatus;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.printjob.api.request.ConsumptionReconciliationRequest;
import com.memento3d.printjob.api.request.CreatePrintJobRequest;
import com.memento3d.printjob.api.request.MaterialIssueRequest;
import com.memento3d.printjob.api.request.ReserveStockRequest;
import com.memento3d.printjob.api.response.PrintJobResponse;
import com.memento3d.printjob.application.service.PrintJobService;
import com.memento3d.printjob.domain.model.PrintJob;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobRepository;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.domain.model.ProductAvailability;
import com.memento3d.products.domain.model.ProductCategory;
import com.memento3d.products.infrastructure.persistence.repository.ProductCategoryRepository;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import com.memento3d.scans.domain.model.InputMode;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.scans.domain.model.ScanStatus;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import com.memento3d.warehouse.api.request.AssignMemberRequest;
import com.memento3d.warehouse.api.request.CreateWarehouseRequest;
import com.memento3d.warehouse.api.response.WarehouseResponse;
import com.memento3d.warehouse.application.service.WarehouseService;
import com.memento3d.warehouse.domain.model.WarehouseRole;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
public class InventoryDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialService materialService;
    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;
    private final PrintJobService printJobService;
    private final PrintJobRepository printJobRepository;
    private final PrinterRepository printerRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ScanRepository scanRepository;

    public InventoryDataSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            MaterialRepository materialRepository,
            WarehouseRepository warehouseRepository,
            MaterialService materialService,
            WarehouseService warehouseService,
            InventoryService inventoryService,
            PrintJobService printJobService,
            PrintJobRepository printJobRepository,
            PrinterRepository printerRepository,
            ProductCategoryRepository categoryRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            CartRepository cartRepository,
            ScanRepository scanRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.materialRepository = materialRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialService = materialService;
        this.warehouseService = warehouseService;
        this.inventoryService = inventoryService;
        this.printJobService = printJobService;
        this.printJobRepository = printJobRepository;
        this.printerRepository = printerRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.scanRepository = scanRepository;
    }

    @Override
    public void run(String... args) {
        log.info("========== Starting Memento3D Master Data Seeding ==========");
        try {
            seedUsers();
            seedWarehousesAndMaterials();
            seedPrinters();
            seedProductsAndCategories();
            seedPrintJobs();
            seedOrdersAndOrderItems();
            seedCart();
            seedScans();
            log.info("========== Memento3D Master Data Seed Completed Successfully! ==========");
        } catch (Exception e) {
            log.error("Error during initial data seeding: {}", e.getMessage(), e);
        }
    }

    private void seedUsers() {
        log.info("Checking system demo users...");
        createUserIfMissing("admin@memento3d.com", "Admin Memento3D", Role.ROLE_ADMIN);
        createUserIfMissing("manager@memento3d.com", "Trần Quản Lý Vận Hành", Role.ROLE_MANAGER);
        createUserIfMissing("warehouse@memento3d.com", "Nguyễn Thủ Kho Tổng", Role.ROLE_WAREHOUSE);
        createUserIfMissing("operator@memento3d.com", "Lê Kỹ Thuật Viên 3D", Role.ROLE_OPERATOR);
        createUserIfMissing("provider@memento3d.com", "Phan Xưởng In 3D Partner", Role.ROLE_PROVIDER);
        createUserIfMissing("driver@memento3d.com", "Hoàng Giao Hàng Express", Role.ROLE_DRIVER);
        createUserIfMissing("user@memento3d.com", "Đặng Khách Hàng Thân Thiết", Role.ROLE_USER);
    }

    private User createUserIfMissing(String email, String fullName, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            log.info("Creating demo account: {} ({})", email, role);
            User u = User.builder()
                    .email(email)
                    .fullName(fullName)
                    .role(role)
                    .password(passwordEncoder.encode("123456"))
                    .provider(AuthProvider.LOCAL)
                    .storageQuotaBytes(10737418240L)
                    .storageUsedBytes(0L)
                    .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200")
                    .build();
            return userRepository.save(u);
        });
    }

    private void seedWarehousesAndMaterials() {
        if (materialRepository.count() > 0 && warehouseRepository.count() > 0) {
            log.info("Materials and Warehouses already seeded. Skipping.");
            return;
        }

        log.info("Seeding Materials and Facilities...");
        // 1. Seed Materials
        MaterialResponse plaWhite = materialService.createMaterial(CreateMaterialRequest.builder()
                .sku("PLA-WHT-001")
                .name("eSun PLA+ Cold White")
                .materialType(MaterialType.PLA)
                .color("Cold White")
                .colorHex("#F8FAFC")
                .density(new BigDecimal("1.24"))
                .diameter(new BigDecimal("1.75"))
                .printTempMin(205)
                .printTempMax(225)
                .bedTempMin(55)
                .bedTempMax(65)
                .unitCost(new BigDecimal("250.00"))
                .description("High-rigidity PLA+ for detailed engineering miniatures")
                .build(), null);

        MaterialResponse plaBlack = materialService.createMaterial(CreateMaterialRequest.builder()
                .sku("PLA-BLK-002")
                .name("Polymaker PolyLite PLA Galaxy Black")
                .materialType(MaterialType.PLA)
                .color("Galaxy Black")
                .colorHex("#0F172A")
                .density(new BigDecimal("1.24"))
                .diameter(new BigDecimal("1.75"))
                .printTempMin(200)
                .printTempMax(220)
                .bedTempMin(50)
                .bedTempMax(60)
                .unitCost(new BigDecimal("280.00"))
                .description("Matte black surface finish with sparkle flakes")
                .build(), null);

        MaterialResponse petgOrange = materialService.createMaterial(CreateMaterialRequest.builder()
                .sku("PETG-ORG-003")
                .name("Prusament PETG Prusa Orange")
                .materialType(MaterialType.PETG)
                .color("Prusa Orange")
                .colorHex("#F97316")
                .density(new BigDecimal("1.27"))
                .diameter(new BigDecimal("1.75"))
                .printTempMin(230)
                .printTempMax(250)
                .bedTempMin(75)
                .bedTempMax(85)
                .unitCost(new BigDecimal("350.00"))
                .description("Impact and heat resistant mechanical prototype material")
                .build(), null);

        MaterialResponse absGrey = materialService.createMaterial(CreateMaterialRequest.builder()
                .sku("ABS-GRY-004")
                .name("Sunlu ABS Industrial Grey")
                .materialType(MaterialType.ABS)
                .color("Industrial Grey")
                .colorHex("#64748B")
                .density(new BigDecimal("1.04"))
                .diameter(new BigDecimal("1.75"))
                .printTempMin(240)
                .printTempMax(260)
                .bedTempMin(95)
                .bedTempMax(110)
                .unitCost(new BigDecimal("290.00"))
                .description("High-temperature functional parts for enclosed printers")
                .build(), null);

        materialService.createMaterial(CreateMaterialRequest.builder()
                .sku("RESIN-CLR-005")
                .name("Anycubic Standard UV Resin Clear")
                .materialType(MaterialType.RESIN)
                .color("Crystal Clear")
                .colorHex("#E2E8F0")
                .density(new BigDecimal("1.15"))
                .unitCost(new BigDecimal("750.00"))
                .description("405nm photopolymer resin for ultra-high resolution DLP printing")
                .build(), null);

        // 2. Seed Warehouses
        WarehouseResponse whHcm = warehouseService.createWarehouse(CreateWarehouseRequest.builder()
                .code("WH-HCM-01")
                .name("Central Hub Thu Duc")
                .location("Lot T2-4, D1 Street, Saigon High-Tech Park, Thu Duc City, HCMC")
                .build(), null);

        WarehouseResponse whHn = warehouseService.createWarehouse(CreateWarehouseRequest.builder()
                .code("WH-HN-02")
                .name("North FabLab Cau Giay")
                .location("No. 18 Duy Tan Street, Dich Vong Hau, Cau Giay, Hanoi")
                .build(), null);

        WarehouseResponse whDn = warehouseService.createWarehouse(CreateWarehouseRequest.builder()
                .code("WH-DN-03")
                .name("Central Hub Da Nang")
                .location("Road 3, Hoa Khanh Industrial Zone, Lien Chieu, Da Nang")
                .build(), null);

        // 3. Assign Staff Memberships to Warehouses
        User adminUser = userRepository.findByEmail("admin@memento3d.com").orElse(null);
        UUID adminId = adminUser != null ? adminUser.getId() : null;

        try {
            warehouseService.assignMember(whHcm.getId(), AssignMemberRequest.builder()
                    .email("manager@memento3d.com")
                    .role(WarehouseRole.MANAGER)
                    .build(), adminId, "ROLE_ADMIN");

            warehouseService.assignMember(whHcm.getId(), AssignMemberRequest.builder()
                    .email("warehouse@memento3d.com")
                    .role(WarehouseRole.WAREHOUSE_STAFF)
                    .build(), adminId, "ROLE_ADMIN");

            warehouseService.assignMember(whHcm.getId(), AssignMemberRequest.builder()
                    .email("operator@memento3d.com")
                    .role(WarehouseRole.PRINT_OPERATOR)
                    .build(), adminId, "ROLE_ADMIN");

            warehouseService.assignMember(whHn.getId(), AssignMemberRequest.builder()
                    .email("warehouse@memento3d.com")
                    .role(WarehouseRole.WAREHOUSE_STAFF)
                    .build(), adminId, "ROLE_ADMIN");
        } catch (Exception e) {
            log.warn("Notice during warehouse membership assignment: {}", e.getMessage());
        }

        // 4. Seed Stock In (Goods Receipts)
        inventoryService.createReceipt(CreateStockReceiptRequest.builder()
                .warehouseId(whHcm.getId())
                .materialId(plaWhite.getId())
                .quantityGram(new BigDecimal("5000.00"))
                .unitCost(new BigDecimal("250.00"))
                .supplier("eSun Vietnam Distributor")
                .batchNumber("LOT-202610-01")
                .idempotencyKey("SEED-REC-001")
                .note("Initial stock import 5kg cold white")
                .build(), null);

        inventoryService.createReceipt(CreateStockReceiptRequest.builder()
                .warehouseId(whHcm.getId())
                .materialId(petgOrange.getId())
                .quantityGram(new BigDecimal("3000.00"))
                .unitCost(new BigDecimal("350.00"))
                .supplier("Prusa Research Direct")
                .batchNumber("LOT-202610-02")
                .idempotencyKey("SEED-REC-002")
                .note("Initial stock import 3kg PETG orange")
                .build(), null);

        inventoryService.createReceipt(CreateStockReceiptRequest.builder()
                .warehouseId(whHn.getId())
                .materialId(plaBlack.getId())
                .quantityGram(new BigDecimal("4000.00"))
                .unitCost(new BigDecimal("280.00"))
                .supplier("Polymaker Global")
                .batchNumber("LOT-202610-03")
                .idempotencyKey("SEED-REC-003")
                .note("Initial stock import Hanoi FabLab")
                .build(), null);
    }

    private void seedPrinters() {
        if (printerRepository.count() > 0) {
            log.info("Printers already seeded. Skipping.");
            return;
        }

        log.info("Seeding 3D Print Farm Fleet...");
        List<Printer> fleet = List.of(
                Printer.builder()
                        .name("Bambu Lab X1-Carbon Combo #01")
                        .model("Bambu Lab X1-Carbon Combo AMS")
                        .serialNumber("BL-X1C-2026-001")
                        .provider(PrinterProvider.OCTOPRINT)
                        .status(PrinterStatus.PRINTING)
                        .build(),
                Printer.builder()
                        .name("Prusa i3 MK3S+ #02")
                        .model("Prusa i3 MK3S+ Multi-Material")
                        .serialNumber("PRUSA-MK3S-2026-002")
                        .provider(PrinterProvider.OCTOPRINT)
                        .status(PrinterStatus.IDLE)
                        .build(),
                Printer.builder()
                        .name("Elegoo Saturn 3 Ultra 12K #03")
                        .model("Elegoo Saturn 3 Ultra MSLA 12K")
                        .serialNumber("ELEGOO-S3U-2026-003")
                        .provider(PrinterProvider.OTHER)
                        .status(PrinterStatus.IDLE)
                        .build(),
                Printer.builder()
                        .name("Formlabs Form 3+ SLA #04")
                        .model("Formlabs Form 3+ SLA Precision")
                        .serialNumber("FORMLABS-F3P-2026-004")
                        .provider(PrinterProvider.OTHER)
                        .status(PrinterStatus.IDLE)
                        .build(),
                Printer.builder()
                        .name("Creality K1 Max High-Speed #05")
                        .model("Creality K1 Max 600mm/s CoreXY")
                        .serialNumber("CREALITY-K1M-2026-005")
                        .provider(PrinterProvider.OCTOPRINT)
                        .status(PrinterStatus.MAINTENANCE)
                        .build()
        );
        printerRepository.saveAll(fleet);
        log.info("Seeded {} physical 3D printers into farm.", fleet.size());
    }

    private void seedProductsAndCategories() {
        log.info("Checking product categories and catalog...");
        Map<String, ProductCategory> categoryMap = new HashMap<>();

        // Ensure 8 Categories
        Object[][] catDefs = {
                {"c1111111-1111-1111-1111-111111111111", "Mini Keepsake", "mini-keepsake", "Chế tác phù điêu 3D bỏ túi, móc khóa bền chắc bằng nhựa quang hóa cao cấp.", "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500", 1},
                {"c2222222-2222-2222-2222-222222222222", "Standard Keepsake", "standard-keepsake", "Tượng chân dung desk sculpture chi tiết cao kèm đế gỗ tự nhiên.", "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=500", 2},
                {"c3333333-3333-3333-3333-333333333333", "Premium Keepsake", "premium-keepsake", "Tác phẩm Signature Memorial tỉ lệ bảo tàng, xử lý bề mặt thủ công tinh xảo.", "https://images.unsplash.com/photo-1544717305-2782549b5136?w=500", 3},
                {"c4444444-4444-4444-4444-444444444444", "Keychain & Charm", "keychain-charm", "Móc khóa 3D mini chân dung cá nhân, phụ kiện balo, chìa khóa độc bản.", "https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=500", 4},
                {"c5555555-5555-5555-5555-555555555555", "Figurine & Bust", "figurine-bust", "Tượng bán thân và chibi 3D tái hiện sống động dáng hình và biểu cảm.", "https://images.unsplash.com/photo-1563245372-f21724e3856d?w=500", 5},
                {"c6666666-6666-6666-6666-666666666666", "Graduation Gift", "graduation-gift", "Quà tặng lễ tốt nghiệp 3D lưu giữ khoảnh khắc cử nhân vinh danh.", "https://images.unsplash.com/photo-1523050854058-8df90110c9f1?w=500", 6},
                {"c7777777-7777-7777-7777-777777777777", "Couple Gift", "couple-gift", "Kỷ vật tình yêu 3D đôi bạn, quà cưới và kỷ niệm ngày yêu.", "https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=500", 7},
                {"c8888888-8888-8888-8888-888888888888", "Family Gift", "family-gift", "Tác phẩm sum vầy gia đình 3D gắn kết thế hệ và kỷ niệm ấm cúng.", "https://images.unsplash.com/photo-1511895426328-dc8714191300?w=500", 8}
        };

        for (Object[] def : catDefs) {
            String slug = (String) def[2];
            UUID catId = UUID.fromString((String) def[0]);
            ProductCategory category = categoryRepository.findBySlug(slug)
                    .or(() -> categoryRepository.findById(catId))
                    .orElseGet(() -> {
                        try {
                            ProductCategory c = ProductCategory.builder()
                                    .id(catId)
                                    .name((String) def[1])
                                    .slug(slug)
                                    .description((String) def[3])
                                    .imageUrl((String) def[4])
                                    .displayOrder((Integer) def[5])
                                    .active(true)
                                    .build();
                            return categoryRepository.save(c);
                        } catch (Exception ex) {
                            log.warn("Category {} already exists or managed: {}", slug, ex.getMessage());
                            return categoryRepository.findBySlug(slug).orElse(null);
                        }
                    });
            if (category != null) {
                categoryMap.put(slug, category);
            }
        }

        // Ensure 16 Products (2 per Category)
        Object[][] prodDefs = {
                // Cat 1: mini-keepsake
                {"d1111111-1111-1111-1111-111111111111", "mini-keepsake", "Mini Keepsake Charm (5cm)", "mini-keepsake-charm-5cm", "Móc khóa phù điêu 3D bỏ túi độ phân giải cao, đúc từ photopolymer resin siêu bền, chống va đập và chống trầy xước.", "Móc khóa 3D bỏ túi bền chắc, độ chi tiết siêu nét.", new BigDecimal("150000"), "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=600", "[\"https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800\",\"https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=800\"]", true, true},
                {"d1222222-1111-1111-1111-111111111111", "mini-keepsake", "Pocket Memorial Lithophane 3D (6cm)", "pocket-memorial-lithophane-3d-6cm", "Tranh thấu quang Lithophane 3D dạng thẻ bỏ túi. Hình ảnh chân dung nổi khối hiện lên sống động đầy cảm xúc khi có ánh sáng chiếu qua từ đèn flash điện thoại.", "Tranh thấu quang 3D phát sáng kỳ diệu khi chiếu đèn.", new BigDecimal("180000"), "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=600", "[\"https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=800\"]", true, false},

                // Cat 2: standard-keepsake
                {"d2111111-2222-2222-2222-222222222222", "standard-keepsake", "Tượng Chân Dung Desk Sculpture (15cm)", "tuong-chan-dung-desk-sculpture-15cm", "Chi tiết đường nét khuôn mặt được dựng hình sắc nét từ ảnh chụp bằng AI neural photogrammetry, hoàn thiện kèm đế gỗ sồi tự nhiên sang trọng.", "Tượng chân dung để bàn kèm đế gỗ cao cấp, kích thước 15cm.", new BigDecimal("350000"), "https://images.unsplash.com/photo-1544717305-2782549b5136?w=600", "[\"https://images.unsplash.com/photo-1544717305-2782549b5136?w=800\"]", true, true},
                {"d2222222-2222-2222-2222-222222222222", "standard-keepsake", "Phù Điêu Chân Dung Nổi Bàn Làm Việc (14cm)", "phu-dieu-chan-dung-noi-ban-lam-viec-14cm", "Phù điêu bán diện 3D nổi khối nghệ thuật, sắc nét từng góc cạnh ánh sáng. Thích hợp trang trí bàn làm việc, kệ sách cá nhân.", "Phù điêu 3D nổi khối góc nhìn nghệ thuật hiện đại.", new BigDecimal("390000"), "https://images.unsplash.com/photo-1578328819058-b69f3a3b0f6b?w=600", "[\"https://images.unsplash.com/photo-1578328819058-b69f3a3b0f6b?w=800\"]", true, false},

                // Cat 3: premium-keepsake
                {"d3111111-3333-3333-3333-333333333333", "premium-keepsake", "Tác Phẩm Signature Memorial (22cm)", "tac-pham-signature-memorial-22cm", "Tượng trưng bày kích thước lớn đạt tiêu chuẩn bảo tàng nghệ thuật. Sử dụng nhựa resin quang hóa cao cấp phủ patina đồng/kim loại cổ điển, khắc tên laser.", "Tượng tỷ lệ bảo tàng 22cm, hoàn thiện patina thủ công độc bản.", new BigDecimal("750000"), "https://images.unsplash.com/photo-1569173112611-52a7cd38bea9?w=600", "[\"https://images.unsplash.com/photo-1569173112611-52a7cd38bea9?w=800\"]", true, true},
                {"d3222222-3333-3333-3333-333333333333", "premium-keepsake", "Imperial Bust Sculpture Nghệ Thuật (25cm)", "imperial-bust-sculpture-nghe-thuat-25cm", "Phiên bản tượng bán thân cao cấp nhất, tỷ lệ 1:6 hoàn thiện nhám mờ sang trọng với lớp phủ bảo vệ nano chống UV vĩnh cửu.", "Tượng bán thân 25cm dát viền vàng nghệ thuật đỉnh cao.", new BigDecimal("950000"), "https://images.unsplash.com/photo-1554188248-986adbb73be4?w=600", "[\"https://images.unsplash.com/photo-1554188248-986adbb73be4?w=800\"]", true, true},

                // Cat 4: keychain-charm
                {"d4111111-4444-4444-4444-444444444444", "keychain-charm", "Móc Khóa Balo Mini Avatar 3D (4.5cm)", "moc-khoa-balo-mini-avatar-3d-4-5cm", "Móc khóa cá nhân hóa tạo hình avatar 3D ngộ nghĩnh, móc kim loại titanium chống gỉ sét, kèm chuông và dây da bền bỉ.", "Móc khóa avatar 3D mini kèm khoen titanium siêu bền.", new BigDecimal("120000"), "https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=600", "[\"https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=800\"]", true, true},
                {"d4222222-4444-4444-4444-444444444444", "keychain-charm", "Charm Bỏ Túi Mascot Kỷ Niệm (5cm)", "charm-bo-tui-mascot-ky-niem-5cm", "Charm thú cưng hoặc linh vật biểu trưng 3D đúc nguyên khối, mạ màu pastel dịu mắt, thích hợp gắn chìa khóa xe hoặc túi xách.", "Charm linh vật biểu trưng đúc nguyên khối pastel cao cấp.", new BigDecimal("135000"), "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600", "[\"https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=800\"]", true, false},

                // Cat 5: figurine-bust
                {"d5111111-5555-5555-5555-555555555555", "figurine-bust", "Tượng Bán Thân Chibi Nghệ Thuật (12cm)", "tuong-ban-than-chibi-nghe-thuat-12cm", "Phong cách chibi hóa chân dung đáng yêu nhưng vẫn giữ trọn vẹn đặc điểm nhận dạng khuôn mặt và nụ cười của bạn.", "Tượng chibi 3D tái hiện biểu cảm sinh động, đáng yêu.", new BigDecimal("290000"), "https://images.unsplash.com/photo-1563245372-f21724e3856d?w=600", "[\"https://images.unsplash.com/photo-1563245372-f21724e3856d?w=800\"]", true, true},
                {"d5222222-5555-5555-5555-555555555555", "figurine-bust", "Tượng Heroic Pose Cá Nhân Hóa (18cm)", "tuong-heroic-pose-ca-nhan-hoa-18cm", "Biến hóa bạn thành nhân vật siêu anh hùng hoặc chiến binh huyền thoại với dáng đứng oai phong theo mô hình 3D tỉ lệ chuẩn.", "Tượng phong cách anh hùng cá nhân hóa tư thế mạnh mẽ.", new BigDecimal("480000"), "https://images.unsplash.com/photo-1608889825205-eebdb9fc5806?w=600", "[\"https://images.unsplash.com/photo-1608889825205-eebdb9fc5806?w=800\"]", true, false},

                // Cat 6: graduation-gift
                {"d6111111-6666-6666-6666-666666666666", "graduation-gift", "Kỷ Niệm Tốt Nghiệp Graduation Cap 3D (16cm)", "ky-niem-tot-nghiep-graduation-cap-3d", "Tượng cử nhân 3D mặc lễ phục tốt nghiệp, tay cầm bằng và mũ cử nhân được cá nhân hóa khuôn mặt người nhận.", "Tượng cử nhân lưu niệm tốt nghiệp vinh danh ngày trọng đại.", new BigDecimal("420000"), "https://images.unsplash.com/photo-1523050854058-8df90110c9f1?w=600", "[\"https://images.unsplash.com/photo-1523050854058-8df90110c9f1?w=800\"]", true, false},
                {"d6222222-6666-6666-6666-666666666666", "graduation-gift", "Bộ Tượng Cử Nhân Danh Dự Honor Graduate (18cm)", "bo-tuong-cu-nhan-danh-du-honor-graduate-18cm", "Phiên bản cử nhân danh dự kèm bục vinh danh khắc tên trường, niên khóa và lời chúc mừng từ gia đình, thầy cô.", "Tượng cử nhân kèm bục vinh danh khắc tên trường sang trọng.", new BigDecimal("490000"), "https://images.unsplash.com/photo-1627556704290-2b1f5853ff78?w=600", "[\"https://images.unsplash.com/photo-1627556704290-2b1f5853ff78?w=800\"]", true, true},

                // Cat 7: couple-gift
                {"d7111111-7777-7777-7777-777777777777", "couple-gift", "Tượng Đôi Uyên Ương Couple Love 3D (16cm)", "tuong-doi-uyen-uong-couple-love-3d", "Tác phẩm điêu khắc đôi 3D tái hiện khoảnh khắc lãng mạn của hai bạn từ ảnh chụp. Món quà tuyệt vời cho dịp kỷ niệm ngày yêu hay ngày cưới.", "Tượng cặp đôi 3D cá nhân hóa kỷ niệm tình yêu vĩnh cửu.", new BigDecimal("520000"), "https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=600", "[\"https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=800\"]", true, true},
                {"d7222222-7777-7777-7777-777777777777", "couple-gift", "Khung Tranh Phù Điêu Nhẫn Cưới Wedding Vow 3D", "khung-tranh-phu-dieu-nhan-cuoi-wedding-vow-3d", "Khung tranh hộp kính 3D kết hợp đôi nhẫn đúc nổi và chân dung cô dâu chú rể ngày trọng đại, lưu giữ lời thề nguyền trọn đời.", "Khung tranh phù điêu 3D đám cưới lưu giữ khoảnh khắc thiêng liêng.", new BigDecimal("650000"), "https://images.unsplash.com/photo-1515934751635-c81c6bc9a2d8?w=600", "[\"https://images.unsplash.com/photo-1515934751635-c81c6bc9a2d8?w=800\"]", true, false},

                // Cat 8: family-gift
                {"d8111111-8888-8888-8888-888888888888", "family-gift", "Tượng Sum Vầy Gia Đình Family Forever 3D (20cm)", "tuong-sum-vay-gia-dinh-family-forever-3d", "Bức tranh phù điêu 3D gia đình đa thế hệ, lưu giữ trọn vẹn nụ cười và khoảnh khắc đoàn tụ ấm áp. Đế gỗ óc chó cao cấp khắc gia huy.", "Phù điêu 3D gia đình lưu giữ khoảnh khắc sum họp ấm cúng.", new BigDecimal("890000"), "https://images.unsplash.com/photo-1511895426328-dc8714191300?w=600", "[\"https://images.unsplash.com/photo-1511895426328-dc8714191300?w=800\"]", true, true},
                {"d8222222-8888-8888-8888-888888888888", "family-gift", "Cây Gia Phả Phù Điêu Thế Hệ Multi-Gen Heritage (24cm)", "cay-gia-pha-phu-dieu-the-he-multi-gen-heritage-24cm", "Kiến trúc điêu khắc cây gia phả 3D với từng nhánh gắn kết hình ảnh nổi các thế hệ ông bà, cha mẹ và con cháu, là báu vật truyền đời.", "Tác phẩm cây gia phả 3D kết nối truyền thống gia đình.", new BigDecimal("1150000"), "https://images.unsplash.com/photo-1581579438747-1dc8d17bbce4?w=600", "[\"https://images.unsplash.com/photo-1581579438747-1dc8d17bbce4?w=800\"]", true, false}
        };

        for (Object[] def : prodDefs) {
            String slug = (String) def[3];
            UUID prodId = UUID.fromString((String) def[0]);
            if (!productRepository.existsBySlug(slug) && !productRepository.existsById(prodId)) {
                String catSlug = (String) def[1];
                ProductCategory category = categoryMap.get(catSlug);
                if (category != null) {
                    try {
                        Product p = Product.builder()
                                .id(prodId)
                                .category(category)
                                .name((String) def[2])
                                .slug(slug)
                                .description((String) def[4])
                                .shortDescription((String) def[5])
                                .price((BigDecimal) def[6])
                                .thumbnailUrl((String) def[7])
                                .images((String) def[8])
                                .featured((Boolean) def[9])
                                .active((Boolean) def[10])
                                .availability(ProductAvailability.AVAILABLE)
                                .build();
                        productRepository.save(p);
                    } catch (Exception ex) {
                        log.warn("Product {} already exists or managed: {}", slug, ex.getMessage());
                    }
                }
            }
        }

        log.info("Finished product catalog seeding (current count: {}).", productRepository.count());
    }

    private void seedPrintJobs() {
        if (printJobRepository.count() > 0) {
            log.info("Print jobs already seeded. Skipping.");
            return;
        }

        List<Material> materials = materialRepository.findAll();
        List<Printer> printers = printerRepository.findAll();
        if (materials.isEmpty()) {
            log.warn("Cannot seed print jobs without materials.");
            return;
        }

        Material pla = materials.get(0);
        Material petg = materials.size() > 2 ? materials.get(2) : pla;
        Printer printerBambu = printers.stream()
                .filter(p -> p.getName().contains("Bambu"))
                .findFirst().orElse(printers.isEmpty() ? null : printers.get(0));

        // Create Print Job 1: In progress on Bambu Lab
        PrintJobResponse j1 = printJobService.createPrintJob(CreatePrintJobRequest.builder()
                .materialId(pla.getId())
                .estimatedGram(new BigDecimal("420.00"))
                .estimatedDurationSeconds(14400)
                .build(), null);

        // Assign to physical printer and set status to PRINTING
        printJobRepository.findById(j1.getId()).ifPresent(pj -> {
            pj.setPrinter(printerBambu);
            pj.setStatus(PrintJobStatus.PRINTING);
            printJobRepository.save(pj);
        });

        // Create Print Job 2: Reserved
        printJobService.createPrintJob(CreatePrintJobRequest.builder()
                .materialId(petg.getId())
                .estimatedGram(new BigDecimal("350.00"))
                .estimatedDurationSeconds(10800)
                .build(), null);

        log.info("Print jobs seeded successfully with physical printer bindings.");
    }

    private void seedOrdersAndOrderItems() {
        if (orderRepository.count() > 0) {
            log.info("Orders already seeded. Skipping.");
            return;
        }

        User customer = userRepository.findByEmail("user@memento3d.com").orElse(null);
        if (customer == null) {
            log.warn("Cannot seed orders: user@memento3d.com not found.");
            return;
        }

        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            log.warn("Cannot seed orders: No products available.");
            return;
        }

        Product coupleProduct = products.stream()
                .filter(p -> p.getSlug().contains("couple"))
                .findFirst().orElse(products.get(0));

        Product memorialProduct = products.stream()
                .filter(p -> p.getSlug().contains("signature-memorial"))
                .findFirst().orElse(products.size() > 1 ? products.get(1) : products.get(0));

        Product gradProduct = products.stream()
                .filter(p -> p.getSlug().contains("tot-nghiep"))
                .findFirst().orElse(products.size() > 2 ? products.get(2) : products.get(0));

        Product charmProduct = products.stream()
                .filter(p -> p.getSlug().contains("charm"))
                .findFirst().orElse(products.size() > 3 ? products.get(3) : products.get(0));

        log.info("Seeding realistic sample orders with full status progression...");

        // Order 1: DELIVERED (VNPAY Paid)
        Order ord1 = Order.builder()
                .orderCode("ORD-20261018-0001")
                .userId(customer.getId())
                .subtotal(coupleProduct.getPrice())
                .totalPrice(coupleProduct.getPrice())
                .shippingFee(BigDecimal.ZERO)
                .currency("VND")
                .status(OrderStatus.DELIVERED)
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.PAID)
                .recipientName("Đặng Thuỳ Trang")
                .phone("0912345678")
                .shippingAddress("Số 128 Đường Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
                .note("Đóng gói hộp quà nhung cao cấp kỷ niệm 2 năm ngày cưới.")
                .items(new ArrayList<>())
                .build();
        ord1.getItems().add(OrderItem.builder()
                .order(ord1)
                .product(coupleProduct)
                .productNameSnapshot(coupleProduct.getName())
                .unitPrice(coupleProduct.getPrice())
                .quantity(1)
                .subtotal(coupleProduct.getPrice())
                .customizationSnapshot("{\"material\": \"Resin Marble White\", \"engraving\": \"Minh & Trang 18.10\"}")
                .build());
        orderRepository.save(ord1);

        // Order 2: PRINTING (MOMO Paid)
        Order ord2 = Order.builder()
                .orderCode("ORD-20261018-0002")
                .userId(customer.getId())
                .subtotal(memorialProduct.getPrice())
                .totalPrice(memorialProduct.getPrice())
                .shippingFee(BigDecimal.ZERO)
                .currency("VND")
                .status(OrderStatus.PRINTING)
                .paymentMethod(PaymentMethod.MOMO)
                .paymentStatus(PaymentStatus.PAID)
                .recipientName("Nguyễn Văn Hùng")
                .phone("0987654321")
                .shippingAddress("Tòa S2.05 VinHomes Grand Park, Phường Long Bình, TP. Thủ Đức, TP. Hồ Chí Minh")
                .note("Tượng trưng bày phòng khách, xử lý màu đồng cổ patina.")
                .items(new ArrayList<>())
                .build();
        ord2.getItems().add(OrderItem.builder()
                .order(ord2)
                .product(memorialProduct)
                .productNameSnapshot(memorialProduct.getName())
                .unitPrice(memorialProduct.getPrice())
                .quantity(1)
                .subtotal(memorialProduct.getPrice())
                .customizationSnapshot("{\"material\": \"Bronze Patina Resin\", \"base\": \"Gỗ óc chó chạm tên\"}")
                .build());
        orderRepository.save(ord2);

        // Order 3: CONFIRMED (VNPAY Paid)
        Order ord3 = Order.builder()
                .orderCode("ORD-20261018-0003")
                .userId(customer.getId())
                .subtotal(gradProduct.getPrice())
                .totalPrice(gradProduct.getPrice())
                .shippingFee(BigDecimal.ZERO)
                .currency("VND")
                .status(OrderStatus.CONFIRMED)
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.PAID)
                .recipientName("Trần Hoàng Nam")
                .phone("0903112233")
                .shippingAddress("Số 45 Đại lộ Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh")
                .note("Quà tặng tốt nghiệp cử nhân Bách Khoa HCMUT.")
                .items(new ArrayList<>())
                .build();
        ord3.getItems().add(OrderItem.builder()
                .order(ord3)
                .product(gradProduct)
                .productNameSnapshot(gradProduct.getName())
                .unitPrice(gradProduct.getPrice())
                .quantity(1)
                .subtotal(gradProduct.getPrice())
                .customizationSnapshot("{\"school\": \"HCMUT\", \"faculty\": \"Khoa Khoa Học Máy Tính 2026\"}")
                .build());
        orderRepository.save(ord3);

        // Order 4: PENDING (Cash on Delivery)
        BigDecimal charmSubtotal = charmProduct.getPrice().multiply(BigDecimal.valueOf(2));
        Order ord4 = Order.builder()
                .orderCode("ORD-20261018-0004")
                .userId(customer.getId())
                .subtotal(charmSubtotal)
                .totalPrice(charmSubtotal)
                .shippingFee(BigDecimal.ZERO)
                .currency("VND")
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.CASH)
                .paymentStatus(PaymentStatus.PENDING)
                .recipientName("Lê Minh Quân")
                .phone("0933889900")
                .shippingAddress("Số 88 Phố Cầu Giấy, Phường Quan Hoa, Quận Cầu Giấy, Hà Nội")
                .note("Thanh toán tiền mặt khi nhận hàng (COD). Giao trong giờ hành chính.")
                .items(new ArrayList<>())
                .build();
        ord4.getItems().add(OrderItem.builder()
                .order(ord4)
                .product(charmProduct)
                .productNameSnapshot(charmProduct.getName())
                .unitPrice(charmProduct.getPrice())
                .quantity(2)
                .subtotal(charmSubtotal)
                .customizationSnapshot("{\"material\": \"Resin Crystal Clear\", \"charmRing\": \"Titanium Black\"}")
                .build());
        orderRepository.save(ord4);

        log.info("Successfully seeded 4 realistic customer & manager orders.");
    }

    private void seedCart() {
        User customer = userRepository.findByEmail("user@memento3d.com").orElse(null);
        if (customer == null) return;

        Cart cart = cartRepository.findWithItemsByCustomerId(customer.getId()).orElseGet(() -> {
            Cart c = Cart.builder()
                    .customerId(customer.getId())
                    .items(new ArrayList<>())
                    .build();
            return cartRepository.save(c);
        });

        if (cart.getItems().isEmpty()) {
            List<Product> products = productRepository.findAll();
            if (products.size() >= 2) {
                Product p1 = products.get(0);
                Product p2 = products.get(1);

                CartItem item1 = CartItem.builder()
                        .product(p1)
                        .quantity(1)
                        .unitPrice(p1.getPrice())
                        .customizationData("{\"material\": \"PLA Cold White\", \"engraving\": \"Memento 2026\", \"base\": \"Gỗ sồi cao cấp\"}")
                        .build();

                CartItem item2 = CartItem.builder()
                        .product(p2)
                        .quantity(2)
                        .unitPrice(p2.getPrice())
                        .customizationData("{\"material\": \"Resin Crystal Clear\", \"charmType\": \"Titanium Ring\"}")
                        .build();

                cart.addItem(item1);
                cart.addItem(item2);
                cartRepository.save(cart);
                log.info("Seeded 2 cart items for user@memento3d.com.");
            }
        }
    }

    private void seedScans() {
        if (scanRepository.count() > 0) {
            log.info("Scans already seeded. Skipping.");
            return;
        }

        User customer = userRepository.findByEmail("user@memento3d.com").orElse(null);
        if (customer == null) return;

        log.info("Seeding sample 3D photogrammetry scans...");
        Scan s1 = Scan.builder()
                .user(customer)
                .name("Chân Dung Nghệ Thuật Đà Lạt 2026")
                .description("Chùm 36 ảnh chụp 360 độ ánh sáng tự nhiên tại Đà Lạt để tái tạo mô hình 3D tỉ lệ 1:1.")
                .notes("Chụp bằng Sony A7IV lens 50mm f/1.8")
                .inputMode(InputMode.image)
                .status(ScanStatus.ready)
                .fileCount(36)
                .totalSizeBytes(158400000L) // ~158 MB
                .thumbnailPath("https://images.unsplash.com/photo-1544717305-2782549b5136?w=600")
                .build();

        Scan s2 = Scan.builder()
                .user(customer)
                .name("Kỷ Niệm Tốt Nghiệp Bách Khoa HCMUT")
                .description("Mô hình tái dựng trang phục cử nhân và biểu cảm khuôn mặt ngày nhận bằng tốt nghiệp.")
                .notes("AI Neural Radiance Fields + Gaussian Splatting")
                .inputMode(InputMode.image)
                .status(ScanStatus.done)
                .fileCount(48)
                .totalSizeBytes(212000000L) // ~212 MB
                .thumbnailPath("https://images.unsplash.com/photo-1523050854058-8df90110c9f1?w=600")
                .build();

        scanRepository.saveAll(List.of(s1, s2));
        log.info("Seeded 2 3D reconstruction scans.");
    }
}
