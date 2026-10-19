package com.memento3d.notification.infrastructure.email;

import com.memento3d.notification.service.EmailService;
import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SMTP implementation of {@link EmailService}.
 * <ul>
 *   <li>Delivery runs on a small dedicated pool so SMTP latency never changes API response time
 *       (important: a timing difference would leak whether an email address exists).</li>
 *   <li>All failures are swallowed and logged WITHOUT the message body (it contains secrets).</li>
 *   <li>If {@code MAIL_HOST} is not configured, sending is skipped with a warning.</li>
 * </ul>
 */
@Service
@Slf4j
public class SmtpEmailService implements EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;
    private final String fromName;
    private final ExecutorService executor;

    public SmtpEmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:no-reply@memento3d.local}") String fromAddress,
            @Value("${app.mail.from-name:Memento3D}") String fromName
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "mail-sender-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    @Override
    public void sendPasswordResetEmail(String to, String fullName, String resetLink, int ttlMinutes) {
        String body = "<p>Xin chào <strong>" + esc(fullName) + "</strong>,</p>"
                + "<p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản Memento3D của bạn. "
                + "Nhấn vào nút bên dưới để tạo mật khẩu mới. Liên kết có hiệu lực trong <strong>"
                + ttlMinutes + " phút</strong> và chỉ sử dụng được một lần.</p>"
                + button("Đặt lại mật khẩu", resetLink)
                + "<p style=\"color:#64748b;font-size:13px\">Nếu nút không hoạt động, hãy sao chép liên kết sau vào trình duyệt:<br>"
                + "<span style=\"word-break:break-all\">" + esc(resetLink) + "</span></p>"
                + "<p style=\"color:#64748b;font-size:13px\">Nếu bạn không yêu cầu điều này, hãy bỏ qua email — "
                + "mật khẩu của bạn sẽ không thay đổi.</p>";
        dispatch(to, "Đặt lại mật khẩu Memento3D", layout("Đặt lại mật khẩu", body), "password-reset");
    }

    @Override
    public void sendPasswordChangedNotification(String to, String fullName) {
        String body = "<p>Xin chào <strong>" + esc(fullName) + "</strong>,</p>"
                + "<p>Mật khẩu tài khoản Memento3D của bạn vừa được thay đổi. Mọi phiên đăng nhập cũ đã bị đăng xuất.</p>"
                + "<p style=\"color:#b91c1c\"><strong>Nếu không phải bạn thực hiện</strong>, hãy đặt lại mật khẩu ngay "
                + "bằng chức năng \"Quên mật khẩu\" và liên hệ bộ phận hỗ trợ.</p>";
        dispatch(to, "Mật khẩu Memento3D của bạn đã được thay đổi", layout("Mật khẩu đã thay đổi", body), "password-changed");
    }

    @Override
    public void sendGoogleAccountNotice(String to, String fullName) {
        String body = "<p>Xin chào <strong>" + esc(fullName) + "</strong>,</p>"
                + "<p>Có yêu cầu đặt lại mật khẩu cho email này. Tài khoản của bạn đăng nhập bằng <strong>Google</strong> "
                + "và chưa thiết lập mật khẩu riêng cho Memento3D nên không có mật khẩu để đặt lại.</p>"
                + "<p>Hãy chọn \"Đăng nhập với Google\" trên trang đăng nhập. Để bảo vệ tài khoản, "
                + "bạn có thể quản lý mật khẩu và xác minh 2 bước tại trang bảo mật tài khoản Google.</p>"
                + "<p style=\"color:#64748b;font-size:13px\">Nếu bạn không yêu cầu điều này, hãy bỏ qua email.</p>";
        dispatch(to, "Tài khoản Memento3D đăng nhập bằng Google", layout("Tài khoản đăng nhập bằng Google", body), "google-notice");
    }

    // ---------------------------------------------------------------------------------------

    private void dispatch(String to, String subject, String html, String kind) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("Email delivery disabled (MAIL_HOST not configured). Skipped '{}' email.", kind);
            return;
        }
        try {
            executor.execute(() -> deliver(sender, to, subject, html, kind));
        } catch (RuntimeException e) {
            log.error("Could not queue '{}' email: {}", kind, e.getClass().getSimpleName());
        }
    }

    private void deliver(JavaMailSender sender, String to, String subject, String html, String kind) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("Sent '{}' email", kind);
        } catch (Exception e) {
            // Never log the body/link: it carries the reset token.
            log.error("Failed to send '{}' email: {}", kind, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private static String button(String label, String href) {
        return "<p style=\"margin:24px 0\"><a href=\"" + esc(href) + "\" "
                + "style=\"background:linear-gradient(135deg,#0ea5e9,#8b5cf6);color:#ffffff;text-decoration:none;"
                + "padding:12px 28px;border-radius:8px;font-weight:700;display:inline-block\">" + esc(label) + "</a></p>";
    }

    private static String layout(String title, String bodyHtml) {
        return "<!DOCTYPE html><html lang=\"vi\"><body style=\"margin:0;background:#f1f5f9;font-family:Segoe UI,Arial,sans-serif;color:#0f172a\">"
                + "<div style=\"max-width:560px;margin:32px auto;background:#ffffff;border-radius:16px;overflow:hidden;"
                + "box-shadow:0 4px 24px rgba(15,23,42,.08)\">"
                + "<div style=\"background:linear-gradient(135deg,#0ea5e9,#8b5cf6);padding:20px 28px;color:#ffffff\">"
                + "<div style=\"font-size:20px;font-weight:800\">Memento3D</div></div>"
                + "<div style=\"padding:28px;line-height:1.6;font-size:15px\"><h2 style=\"margin-top:0\">" + esc(title) + "</h2>"
                + bodyHtml + "</div>"
                + "<div style=\"padding:16px 28px;background:#f8fafc;color:#94a3b8;font-size:12px\">"
                + "Email tự động từ Memento3D — vui lòng không trả lời.</div></div></body></html>";
    }
}
