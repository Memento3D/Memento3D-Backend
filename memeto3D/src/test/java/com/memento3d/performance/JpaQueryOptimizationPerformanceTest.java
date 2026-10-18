package com.memento3d.performance;

import com.memento3d.cart.api.response.CartItemResponse;
import com.memento3d.cart.domain.model.Cart;
import com.memento3d.cart.domain.model.CartItem;
import com.memento3d.cart.infrastructure.persistence.repository.CartRepository;
import com.memento3d.material.domain.model.Material;
import com.memento3d.material.domain.model.MaterialType;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.orders.api.response.OrderResponse;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderItem;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.printers.domain.model.Printer;
import com.memento3d.printers.infrastructure.persistence.repository.PrinterRepository;
import com.memento3d.printjob.api.response.PrintJobResponse;
import com.memento3d.printjob.domain.model.PrintJob;
import com.memento3d.printjob.domain.model.PrintJobStatus;
import com.memento3d.printjob.infrastructure.persistence.repository.PrintJobRepository;
import com.memento3d.products.api.response.ProductResponse;
import com.memento3d.products.domain.model.Product;
import com.memento3d.products.domain.model.ProductCategory;
import com.memento3d.products.infrastructure.persistence.repository.ProductCategoryRepository;
import com.memento3d.products.infrastructure.persistence.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class JpaQueryOptimizationPerformanceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PrintJobRepository printJobRepository;

    @Autowired
    private PrinterRepository printerRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @Test
    @DisplayName("Performance Test: 10 vs 100 vs 1,000 PrintJobs execute identical O(1) query counts")
    void testPrintJobQueryCountScalability_10_100_1000() {
        Printer printer = printerRepository.save(Printer.builder()
                .name("Prusa i3 MK3S")
                .serialNumber("SN-PRUSA-" + UUID.randomUUID())
                .model("MK3S")
                .build());

        Material material = materialRepository.save(Material.builder()
                .sku("MAT-" + UUID.randomUUID())
                .name("PLA Black 1.75mm")
                .materialType(MaterialType.PLA)
                .color("Black")
                .unit("g")
                .build());

        // 1. Seed 10 PrintJobs
        List<PrintJob> batch10 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            batch10.add(PrintJob.builder()
                    .jobNumber("JOB-10-" + i + "-" + UUID.randomUUID())
                    .printer(printer)
                    .material(material)
                    .status(PrintJobStatus.PRINTING)
                    .estimatedGram(BigDecimal.valueOf(50))
                    .build());
        }
        printJobRepository.saveAll(batch10);
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        // Query 10 items
        Page<PrintJob> page10 = printJobRepository.findAllWithFilters(PrintJobStatus.PRINTING, printer.getId(), material.getId(), PageRequest.of(0, 10));
        List<PrintJobResponse> responses10 = page10.map(PrintJobResponse::fromEntity).getContent();
        long queries10 = statistics.getPrepareStatementCount();

        assertThat(responses10).hasSize(10);
        assertThat(responses10.get(0).getPrinterName()).isEqualTo("Prusa i3 MK3S");
        assertThat(responses10.get(0).getMaterialName()).isEqualTo("PLA Black 1.75mm");
        System.out.println(">>> [10 PrintJobs] Actual SQL statements executed: " + queries10);

        // 2. Seed 90 more PrintJobs (total 100)
        List<PrintJob> batch90 = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            batch90.add(PrintJob.builder()
                    .jobNumber("JOB-100-" + i + "-" + UUID.randomUUID())
                    .printer(printer)
                    .material(material)
                    .status(PrintJobStatus.PRINTING)
                    .estimatedGram(BigDecimal.valueOf(50))
                    .build());
        }
        printJobRepository.saveAll(batch90);
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        // Query 10 items from 100 total
        Page<PrintJob> page100 = printJobRepository.findAllWithFilters(PrintJobStatus.PRINTING, printer.getId(), material.getId(), PageRequest.of(0, 10));
        List<PrintJobResponse> responses100 = page100.map(PrintJobResponse::fromEntity).getContent();
        long queries100 = statistics.getPrepareStatementCount();

        assertThat(responses100).hasSize(10);
        assertThat(page100.getTotalElements()).isGreaterThanOrEqualTo(100);
        System.out.println(">>> [100 PrintJobs] Actual SQL statements executed: " + queries100);

        // 3. Seed 900 more PrintJobs (total 1,000)
        List<PrintJob> batch900 = new ArrayList<>();
        for (int i = 0; i < 900; i++) {
            batch900.add(PrintJob.builder()
                    .jobNumber("JOB-1000-" + i + "-" + UUID.randomUUID())
                    .printer(printer)
                    .material(material)
                    .status(PrintJobStatus.PRINTING)
                    .estimatedGram(BigDecimal.valueOf(50))
                    .build());
        }
        printJobRepository.saveAll(batch900);
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        // Query 10 items from 1,000 total
        Page<PrintJob> page1000 = printJobRepository.findAllWithFilters(PrintJobStatus.PRINTING, printer.getId(), material.getId(), PageRequest.of(0, 10));
        List<PrintJobResponse> responses1000 = page1000.map(PrintJobResponse::fromEntity).getContent();
        long queries1000 = statistics.getPrepareStatementCount();

        assertThat(responses1000).hasSize(10);
        assertThat(page1000.getTotalElements()).isGreaterThanOrEqualTo(1000);
        System.out.println(">>> [1,000 PrintJobs] Actual SQL statements executed: " + queries1000);

        // Verify constant O(1) query count: queries do NOT increase linearly with total records!
        assertThat(queries10).isEqualTo(queries100);
        assertThat(queries100).isEqualTo(queries1000);
        assertThat(queries1000).isEqualTo(2); // Exactly 1 select with printer+material joined + 1 count query
    }

    @Test
    @DisplayName("Performance Test: Cart with 10 Items preloads products and categories in exactly 1 query")
    void testCartWithMultipleItems_singleQuery() {
        ProductCategory category = categoryRepository.save(ProductCategory.builder()
                .name("Decor")
                .slug("decor-" + UUID.randomUUID())
                .build());

        UUID customerId = UUID.randomUUID();
        Cart cart = Cart.builder()
                .customerId(customerId)
                .items(new ArrayList<>())
                .build();

        for (int i = 0; i < 10; i++) {
            Product product = productRepository.save(Product.builder()
                    .name("Vase " + i)
                    .slug("vase-" + i + "-" + UUID.randomUUID())
                    .price(BigDecimal.valueOf(150000))
                    .category(category)
                    .active(true)
                    .build());

            CartItem item = CartItem.builder()
                    .product(product)
                    .quantity(2)
                    .unitPrice(BigDecimal.valueOf(150000))
                    .build();
            cart.addItem(item);
        }

        cartRepository.save(cart);
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        // Execute optimized query
        Cart loadedCart = cartRepository.findWithItemsByCustomerId(customerId).orElseThrow();
        List<CartItemResponse> responses = loadedCart.getItems().stream()
                .map(CartItemResponse::fromEntity)
                .toList();

        long sqlCount = statistics.getPrepareStatementCount();
        System.out.println(">>> [Cart with 10 Items] Actual SQL statements executed: " + sqlCount);

        assertThat(responses).hasSize(10);
        assertThat(responses.get(0).getCategoryName()).isEqualTo("Decor");
        assertThat(sqlCount).isEqualTo(1); // Single query with JOIN FETCH / EntityGraph
    }

    @Test
    @DisplayName("Performance Test: Order Listing uses batch fetching for collections (1 batch query instead of N)")
    void testOrderListingWithItems_batchFetch() {
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 10; i++) {
            Order order = Order.builder()
                    .orderCode("ORD-BATCH-" + i + "-" + UUID.randomUUID())
                    .userId(userId)
                    .subtotal(BigDecimal.valueOf(300000))
                    .totalPrice(BigDecimal.valueOf(300000))
                    .status(OrderStatus.CONFIRMED)
                    .items(new ArrayList<>())
                    .build();

            for (int j = 0; j < 3; j++) {
                order.getItems().add(OrderItem.builder()
                        .order(order)
                        .productNameSnapshot("Product " + j)
                        .unitPrice(BigDecimal.valueOf(100000))
                        .quantity(1)
                        .subtotal(BigDecimal.valueOf(100000))
                        .build());
            }
            orderRepository.save(order);
        }

        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        // Paginated query for 10 orders
        Page<Order> orderPage = orderRepository.findByUserId(userId, PageRequest.of(0, 10));
        List<OrderResponse> responses = orderPage.map(OrderResponse::fromEntity).getContent();

        long sqlCount = statistics.getPrepareStatementCount();
        System.out.println(">>> [10 Orders with Items] Actual SQL statements executed: " + sqlCount);

        assertThat(responses).hasSize(10);
        assertThat(responses.get(0).getItems()).hasSize(3);
        // 1 orders query + 1 count query + 1 batch items query = 3 queries (instead of 1 + 1 + 10 = 12 queries!)
        assertThat(sqlCount).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Performance Test: Product Search with 20 Products joins Category in 1 select + 1 count query")
    void testProductSearchWithCategories_singleQuery() {
        ProductCategory category = categoryRepository.save(ProductCategory.builder()
                .name("Mechanical Parts")
                .slug("mech-" + UUID.randomUUID())
                .build());

        for (int i = 0; i < 20; i++) {
            productRepository.save(Product.builder()
                    .name("Gear " + i)
                    .slug("gear-" + i + "-" + UUID.randomUUID())
                    .price(BigDecimal.valueOf(50000))
                    .category(category)
                    .active(true)
                    .build());
        }

        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        Page<Product> page = productRepository.searchProducts(null, null, null, null, null, null, null, PageRequest.of(0, 20));
        List<ProductResponse> responses = page.map(ProductResponse::fromEntity).getContent();
        long sqlCount = statistics.getPrepareStatementCount();

        System.out.println(">>> [20 Products Search] Actual SQL statements executed: " + sqlCount);

        assertThat(responses).hasSize(20);
        assertThat(responses.get(0).getCategoryName()).isEqualTo("Mechanical Parts");
        // Exactly 1 data select (with category joined) + 1 count query = 2 queries
        assertThat(sqlCount).isEqualTo(2);
    }
}
