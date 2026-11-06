package com.memento3d.notification;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.notification.domain.model.OrderEmailNotification;
import com.memento3d.notification.infrastructure.OrderEmailNotificationRepository;
import com.memento3d.notification.infrastructure.email.OrderEmailEventListener;
import com.memento3d.notification.infrastructure.email.OrderEmailServiceImpl;
import com.memento3d.orders.application.service.impl.OrderServiceImpl;
import com.memento3d.orders.domain.event.OrderConfirmedEvent;
import com.memento3d.orders.domain.event.OrderCompletedEvent;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderItem;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.products.domain.model.Product;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEmailNotificationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderEmailNotificationRepository emailNotificationRepository;

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private com.memento3d.shared.outbox.application.service.OutboxPublisherService outboxPublisher;

    @Mock
    private com.memento3d.entitlement.application.service.EntitlementService entitlementService;

    private SpringTemplateEngine templateEngine;
    private OrderEmailServiceImpl orderEmailService;
    private OrderEmailEventListener orderEmailEventListener;

    private UUID orderId;
    private UUID customerId;
    private User customer;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        // Set up SpringTemplateEngine with SpringStandardDialect (SpEL)
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false);

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        orderEmailService = new OrderEmailServiceImpl(
                orderRepository,
                userRepository,
                emailNotificationRepository,
                templateEngine,
                mailSenderProvider
        );

        ReflectionTestUtils.setField(orderEmailService, "fromAddress", "no-reply@memento3d.local");
        ReflectionTestUtils.setField(orderEmailService, "fromName", "Memento3D");
        ReflectionTestUtils.setField(orderEmailService, "frontendBaseUrl", "http://localhost:5173");

        orderEmailEventListener = new OrderEmailEventListener(orderEmailService);

        customerId = UUID.randomUUID();
        customer = User.builder()
                .id(customerId)
                .email("customer@example.com")
                .fullName("Nguyễn Văn Khách")
                .role(Role.ROLE_USER)
                .build();

        orderId = UUID.randomUUID();
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Tượng Kỷ Vật 3D Gia Đình")
                .thumbnailUrl("https://res.cloudinary.com/memento3d/image/upload/v1/sample.png")
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .productNameSnapshot("Tượng Kỷ Vật 3D Gia Đình")
                .materialSnapshot("Resin Cao Cấp")
                .dimensionsSnapshot("15cm")
                .technologySnapshot("SLA (Stereolithography)")
                .finishSnapshot("Phủ bóng UV bảo vệ")
                .unitPrice(BigDecimal.valueOf(450000))
                .quantity(1)
                .subtotal(BigDecimal.valueOf(450000))
                .build();

        sampleOrder = Order.builder()
                .id(orderId)
                .orderCode("M3D-20261025-0001")
                .userId(customerId)
                .status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.VNPAY)
                .subtotal(BigDecimal.valueOf(450000))
                .discountAmount(BigDecimal.valueOf(0))
                .shippingFee(BigDecimal.valueOf(30000))
                .totalPrice(BigDecimal.valueOf(480000))
                .recipientName("Nguyễn Văn Khách")
                .shippingAddress("123 Lê Lợi, Quận 1, TP. Hồ Chí Minh")
                .phone("0901234567")
                .shippingCarrier("Giao Hàng Tiết Kiệm")
                .trackingCode("GHTK-987654321")
                .items(new ArrayList<>(List.of(item)))
                .build();
    }

    @Test
    @DisplayName("Test 1: ORDER_CONFIRMED sends email with correct template and saves status SENT")
    void testOrderConfirmed_Success() {
        when(emailNotificationRepository.existsByOrderIdAndEmailTypeAndStatus(orderId, "ORDER_CONFIRMED", "SENT"))
                .thenReturn(false);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(emailNotificationRepository.findByOrderIdAndEmailType(orderId, "ORDER_CONFIRMED"))
                .thenReturn(Optional.empty());

        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        when(emailNotificationRepository.save(any(OrderEmailNotification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Trigger Event 1
        orderEmailEventListener.onOrderConfirmed(new OrderConfirmedEvent(orderId));

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));

        ArgumentCaptor<OrderEmailNotification> notifCaptor = ArgumentCaptor.forClass(OrderEmailNotification.class);
        verify(emailNotificationRepository, atLeastOnce()).save(notifCaptor.capture());

        OrderEmailNotification savedNotif = notifCaptor.getValue();
        assertEquals("ORDER_CONFIRMED", savedNotif.getEmailType());
        assertEquals("customer@example.com", savedNotif.getRecipientEmail());
        assertEquals("SENT", savedNotif.getStatus());
        assertNotNull(savedNotif.getSentAt());
        assertNull(savedNotif.getLastError());
    }

    @Test
    @DisplayName("Test 2: ORDER_COMPLETED sends completion email with delivery details")
    void testOrderCompleted_Success() {
        sampleOrder.setStatus(OrderStatus.DELIVERED);

        when(emailNotificationRepository.existsByOrderIdAndEmailTypeAndStatus(orderId, "ORDER_COMPLETED", "SENT"))
                .thenReturn(false);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(emailNotificationRepository.findByOrderIdAndEmailType(orderId, "ORDER_COMPLETED"))
                .thenReturn(Optional.empty());

        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        when(emailNotificationRepository.save(any(OrderEmailNotification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Trigger Event 2
        orderEmailEventListener.onOrderCompleted(new OrderCompletedEvent(orderId));

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));

        ArgumentCaptor<OrderEmailNotification> notifCaptor = ArgumentCaptor.forClass(OrderEmailNotification.class);
        verify(emailNotificationRepository, atLeastOnce()).save(notifCaptor.capture());

        OrderEmailNotification savedNotif = notifCaptor.getValue();
        assertEquals("ORDER_COMPLETED", savedNotif.getEmailType());
        assertEquals("SENT", savedNotif.getStatus());
        assertEquals("M3D-20261025-0001", savedNotif.getOrderCode());
    }

    @Test
    @DisplayName("Test 3 & 4: Idempotency prevents duplicate emails when API or Webhook is retried")
    void testIdempotency_PreventsDuplicateSending() {
        // Notification is already recorded as SENT
        when(emailNotificationRepository.existsByOrderIdAndEmailTypeAndStatus(orderId, "ORDER_CONFIRMED", "SENT"))
                .thenReturn(true);

        orderEmailService.processOrderConfirmed(orderId);

        // Verify that neither mail sender nor template processing was called
        verify(javaMailSender, never()).send(any(MimeMessage.class));
        verify(orderRepository, never()).findWithItemsById(any());
    }

    @Test
    @DisplayName("Test 5: SMTP failure marks status FAILED and does NOT throw or rollback order")
    void testSmtpFailure_NonBlockingAndRecordsError() {
        when(emailNotificationRepository.existsByOrderIdAndEmailTypeAndStatus(orderId, "ORDER_CONFIRMED", "SENT"))
                .thenReturn(false);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(emailNotificationRepository.findByOrderIdAndEmailType(orderId, "ORDER_CONFIRMED"))
                .thenReturn(Optional.empty());

        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP connection timeout 10000ms"))
                .when(javaMailSender).send(any(MimeMessage.class));

        when(emailNotificationRepository.save(any(OrderEmailNotification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> orderEmailService.processOrderConfirmed(orderId));

        ArgumentCaptor<OrderEmailNotification> captor = ArgumentCaptor.forClass(OrderEmailNotification.class);
        verify(emailNotificationRepository, atLeastOnce()).save(captor.capture());

        OrderEmailNotification finalNotif = captor.getValue();
        assertEquals("FAILED", finalNotif.getStatus());
        assertEquals(1, finalNotif.getRetryCount());
        assertTrue(finalNotif.getLastError().contains("MailSendException"));
    }

    @Test
    @DisplayName("Test 6: Retry job retries only FAILED notifications with retryCount < 3")
    void testRetryJob_OnlyRetriesFailedEligibleEmails() {
        OrderEmailNotification failedNotif = OrderEmailNotification.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .emailType("ORDER_CONFIRMED")
                .recipientEmail("customer@example.com")
                .orderCode("M3D-20261025-0001")
                .status("FAILED")
                .retryCount(1)
                .build();

        when(emailNotificationRepository.findByStatusAndRetryCountLessThan(eq("FAILED"), eq(3), any(Pageable.class)))
                .thenReturn(List.of(failedNotif));

        when(emailNotificationRepository.existsByOrderIdAndEmailTypeAndStatus(orderId, "ORDER_CONFIRMED", "SENT"))
                .thenReturn(false);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(emailNotificationRepository.findByOrderIdAndEmailType(orderId, "ORDER_CONFIRMED"))
                .thenReturn(Optional.of(failedNotif));

        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(emailNotificationRepository.save(any(OrderEmailNotification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        orderEmailService.retryFailedEmails();

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
        assertEquals("SENT", failedNotif.getStatus());
    }

    @Test
    @DisplayName("Test 10: Recipient is strictly the order owner account, never unauthorized emails")
    void testRecipient_StrictlyOrderCustomerAccount() {
        when(emailNotificationRepository.existsByOrderIdAndEmailTypeAndStatus(orderId, "ORDER_CONFIRMED", "SENT"))
                .thenReturn(false);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(emailNotificationRepository.save(any(OrderEmailNotification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        orderEmailService.processOrderConfirmed(orderId);

        ArgumentCaptor<OrderEmailNotification> captor = ArgumentCaptor.forClass(OrderEmailNotification.class);
        verify(emailNotificationRepository, atLeastOnce()).save(captor.capture());

        assertEquals(customer.getEmail(), captor.getValue().getRecipientEmail());
    }

    @Test
    @DisplayName("Test 12: Transition to READY (print done) does NOT trigger ORDER_COMPLETED event")
    void testTransitionToReady_DoesNotPublishOrderCompletedEvent() {
        Order readyOrder = Order.builder()
                .id(orderId)
                .userId(customerId)
                .status(OrderStatus.PRINTING)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(readyOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderServiceImpl orderService = new OrderServiceImpl(
                orderRepository,
                null,
                null,
                null,
                null,
                null,
                null,
                entitlementService,
                outboxPublisher,
                null,
                applicationEventPublisher,
                null, mock(com.memento3d.customization.application.service.PrintDesignService.class)
        );

        // Transition from PRINTING -> READY (3D printing finished, but not delivered/completed yet)
        orderService.updateOrderStatus(orderId, OrderStatus.READY, UUID.randomUUID(), Role.ROLE_MANAGER);

        // Verify NO OrderCompletedEvent was published
        verify(applicationEventPublisher, never()).publishEvent(any(OrderCompletedEvent.class));
        verify(applicationEventPublisher, never()).publishEvent(any(OrderConfirmedEvent.class));
    }

    @Test
    @DisplayName("Test Event 2 Trigger: Transition to DELIVERED publishes OrderCompletedEvent")
    void testTransitionToDelivered_PublishesOrderCompletedEvent() {
        Order shippedOrder = Order.builder()
                .id(orderId)
                .userId(customerId)
                .status(OrderStatus.SHIPPED)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(shippedOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderServiceImpl orderService = new OrderServiceImpl(
                orderRepository,
                null,
                null,
                null,
                null,
                null,
                null,
                entitlementService,
                outboxPublisher,
                null,
                applicationEventPublisher,
                null, mock(com.memento3d.customization.application.service.PrintDesignService.class)
        );

        // Transition SHIPPED -> DELIVERED
        orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED, UUID.randomUUID(), Role.ROLE_MANAGER);

        // Verify OrderCompletedEvent IS published!
        verify(applicationEventPublisher, times(1)).publishEvent(any(OrderCompletedEvent.class));
    }
}
