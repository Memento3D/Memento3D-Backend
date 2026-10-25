package com.memento3d.notification.infrastructure.email;

import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.notification.domain.model.OrderEmailNotification;
import com.memento3d.notification.infrastructure.OrderEmailNotificationRepository;
import com.memento3d.notification.service.OrderEmailService;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderItem;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEmailServiceImpl implements OrderEmailService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderEmailNotificationRepository emailNotificationRepository;
    private final ITemplateEngine templateEngine;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:no-reply@memento3d.local}")
    private String fromAddress;

    @Value("${app.mail.from-name:Memento3D}")
    private String fromName;

    @Value("${app.frontend.base-url:${FRONTEND_BASE_URL:http://localhost:5173}}")
    private String frontendBaseUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final String TYPE_CONFIRMED = "ORDER_CONFIRMED";
    private static final String TYPE_COMPLETED = "ORDER_COMPLETED";

    @Override
    @Transactional
    public void processOrderConfirmed(UUID orderId) {
        log.info("Processing order confirmed email for orderId={}", orderId);
        processOrderEmail(orderId, TYPE_CONFIRMED, "mail/order-confirmed");
    }

    @Override
    @Transactional
    public void processOrderCompleted(UUID orderId) {
        log.info("Processing order completed email for orderId={}", orderId);
        processOrderEmail(orderId, TYPE_COMPLETED, "mail/order-completed");
    }

    private void processOrderEmail(UUID orderId, String emailType, String templateName) {
        // 1. Idempotency Check: Don't send twice if already SENT
        if (emailNotificationRepository.existsByOrderIdAndEmailTypeAndStatus(orderId, emailType, "SENT")) {
            log.info("Order email [type={}] for orderId={} was already SENT. Skipping duplicate.", emailType, orderId);
            return;
        }

        // 2. Fetch order with items
        Order order = orderRepository.findWithItemsById(orderId)
                .or(() -> orderRepository.findById(orderId))
                .orElse(null);

        if (order == null) {
            log.warn("Cannot send {} email: Order not found with ID={}", emailType, orderId);
            return;
        }

        // 3. Fetch user account email
        User customer = userRepository.findById(order.getUserId()).orElse(null);
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.warn("Cannot send {} email for order {}: Customer account or email not found",
                    emailType, order.getOrderCode());
            return;
        }

        String recipientEmail = customer.getEmail().trim().toLowerCase();
        String customerName = order.getRecipientName() != null && !order.getRecipientName().isBlank()
                ? order.getRecipientName()
                : (customer.getFullName() != null && !customer.getFullName().isBlank() ? customer.getFullName() : "Quý khách");

        // 4. Upsert or get idempotency record
        OrderEmailNotification notification = emailNotificationRepository
                .findByOrderIdAndEmailType(orderId, emailType)
                .orElseGet(() -> OrderEmailNotification.builder()
                        .orderId(orderId)
                        .emailType(emailType)
                        .orderCode(order.getOrderCode())
                        .recipientEmail(recipientEmail)
                        .recipientName(customerName)
                        .status("PENDING")
                        .retryCount(0)
                        .build());

        if ("SENT".equals(notification.getStatus())) {
            return;
        }

        notification.setRecipientEmail(recipientEmail);
        notification.setRecipientName(customerName);
        notification = emailNotificationRepository.save(notification);

        // 5. Subject & View Context
        String subject = TYPE_CONFIRMED.equals(emailType)
                ? "[Memento3D] Đơn hàng #" + order.getOrderCode() + " đã được xác nhận"
                : "[Memento3D] Đơn hàng #" + order.getOrderCode() + " đã hoàn tất";

        Context context = buildTemplateContext(order, customerName, emailType);

        // 6. Render HTML Template
        String htmlContent;
        try {
            htmlContent = templateEngine.process(templateName, context);
        } catch (Exception ex) {
            log.error("Failed to render email template '{}' for order {}: {}", templateName, order.getOrderCode(), ex.getMessage());
            notification.setStatus("FAILED");
            notification.setRetryCount(notification.getRetryCount() + 1);
            notification.setLastError("TemplateRenderError: " + ex.getMessage());
            emailNotificationRepository.save(notification);
            return;
        }

        // 7. Dispatch via JavaMailSender
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("================================================================================");
            log.warn("[DEV MODE / NO SMTP] Mail sender unavailable. Simulated {} email for order {} to {}",
                    emailType, order.getOrderCode(), recipientEmail);
            log.warn("Subject: {}", subject);
            log.warn("================================================================================");
            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
            notification.setLastError(null);
            emailNotificationRepository.save(notification);
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            sender.send(message);

            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
            notification.setLastError(null);
            emailNotificationRepository.save(notification);
            log.info("Successfully sent {} email for order {} to {}", emailType, order.getOrderCode(), recipientEmail);
        } catch (Exception ex) {
            log.error("Failed to send {} email for order {}: {}", emailType, order.getOrderCode(), ex.getMessage());
            notification.setStatus("FAILED");
            notification.setRetryCount(notification.getRetryCount() + 1);
            notification.setLastError(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            emailNotificationRepository.save(notification);
            // Do NOT throw: Order transaction must remain successful even if SMTP fails
        }
    }

    private Context buildTemplateContext(Order order, String customerName, String emailType) {
        Context context = new Context();
        context.setVariable("orderCode", order.getOrderCode());
        context.setVariable("customerName", customerName);

        String orderDate = order.getCreatedAt() != null
                ? DATE_FORMATTER.format(order.getCreatedAt())
                : DATE_FORMATTER.format(Instant.now());
        context.setVariable("orderDate", orderDate);

        String completedDate = order.getUpdatedAt() != null
                ? DATE_FORMATTER.format(order.getUpdatedAt())
                : DATE_FORMATTER.format(Instant.now());
        context.setVariable("completedDate", completedDate);

        // Payment status & method text
        String paymentMethodStr = order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "Chưa xác định";
        String paymentStatusStr = order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "PENDING";
        context.setVariable("paymentMethodText", paymentMethodStr);
        context.setVariable("paymentStatusText", "PAID".equalsIgnoreCase(paymentStatusStr) || "SUCCESS".equalsIgnoreCase(paymentStatusStr)
                ? "Đã thanh toán" : "Chờ thanh toán");

        // Price formatting
        context.setVariable("formattedSubtotal", formatVnd(order.getSubtotal()));
        context.setVariable("discountAmount", order.getDiscountAmount());
        context.setVariable("formattedDiscountAmount", formatVnd(order.getDiscountAmount()));
        context.setVariable("formattedShippingFee", formatVnd(order.getShippingFee()));
        context.setVariable("formattedTotalPrice", formatVnd(order.getTotalPrice()));

        // Delivery details
        context.setVariable("recipientName", order.getRecipientName());
        context.setVariable("shippingAddress", order.getShippingAddress());
        context.setVariable("phone", order.getPhone());
        context.setVariable("shippingCarrier", order.getShippingCarrier());
        context.setVariable("trackingCode", order.getTrackingCode());

        // Order URL
        String sanitizedBaseUrl = frontendBaseUrl != null ? frontendBaseUrl.replaceAll("/+$", "") : "http://localhost:5173";
        context.setVariable("orderDetailUrl", sanitizedBaseUrl + "/orders/" + order.getId());

        // Item View Models
        List<ItemViewModel> itemList = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem oi : order.getItems()) {
                String pName = oi.getProductNameSnapshot();
                if (pName == null || pName.isBlank()) {
                    pName = oi.getProduct() != null ? oi.getProduct().getName() : "Sản phẩm Memento3D";
                }

                String imgUrl = null;
                if (oi.getProduct() != null) {
                    imgUrl = oi.getProduct().getThumbnailUrl();
                }

                itemList.add(ItemViewModel.builder()
                        .productName(pName)
                        .material(oi.getMaterialSnapshot())
                        .dimensions(oi.getDimensionsSnapshot())
                        .technology(oi.getTechnologySnapshot())
                        .finish(oi.getFinishSnapshot())
                        .quantity(oi.getQuantity())
                        .formattedSubtotal(formatVnd(oi.getSubtotal()))
                        .imageUrl(imgUrl)
                        .build());
            }
        }
        context.setVariable("items", itemList);

        return context;
    }

    @Override
    @Scheduled(fixedDelayString = "${app.mail.retry-interval-ms:60000}")
    @Transactional
    public void retryFailedEmails() {
        List<OrderEmailNotification> failedList =
                emailNotificationRepository.findByStatusAndRetryCountLessThan("FAILED", 3, PageRequest.of(0, 10));

        if (failedList.isEmpty()) {
            return;
        }

        log.info("Found {} failed order emails to retry", failedList.size());
        for (OrderEmailNotification notif : failedList) {
            try {
                if (TYPE_CONFIRMED.equals(notif.getEmailType())) {
                    processOrderConfirmed(notif.getOrderId());
                } else if (TYPE_COMPLETED.equals(notif.getEmailType())) {
                    processOrderCompleted(notif.getOrderId());
                }
            } catch (Exception ex) {
                log.error("Retry failed for email notification ID={}: {}", notif.getId(), ex.getMessage());
            }
        }
    }

    private String formatVnd(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("vi", "VN"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        return df.format(amount) + " ₫";
    }

    @Getter
    @Builder
    public static class ItemViewModel {
        private String productName;
        private String material;
        private String dimensions;
        private String technology;
        private String finish;
        private int quantity;
        private String formattedSubtotal;
        private String imageUrl;
    }
}
