package com.memento3d.notification.infrastructure.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Creates the SMTP {@link JavaMailSender} only when {@code MAIL_HOST} is configured.
 * Without it the application still boots; {@link SmtpEmailService} just reports that
 * email delivery is disabled.
 */
@Configuration
@Slf4j
public class MailConfig {

    @Bean
    @ConditionalOnExpression("!'${app.mail.host:}'.isEmpty()")
    public JavaMailSender javaMailSender(
            @Value("${app.mail.host}") String host,
            @Value("${app.mail.port:587}") int port,
            @Value("${app.mail.username:}") String username,
            @Value("${app.mail.password:}") String password,
            @Value("${app.mail.smtp-auth:true}") boolean smtpAuth,
            @Value("${app.mail.starttls:true}") boolean startTls,
            @Value("${app.mail.ssl:false}") boolean ssl
    ) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        if (!username.isBlank()) {
            sender.setUsername(username.trim());
            String cleanedPassword = password == null ? "" : password.trim().replaceAll("\\s+", "");
            sender.setPassword(cleanedPassword);

            if (host.toLowerCase().contains("gmail") && cleanedPassword.length() != 16) {
                log.warn("⚠️ CẢNH BÁO GMAIL SMTP: Mật khẩu ứng dụng Google (App Password) phải có ĐÚNG 16 ký tự. Hiện tại cấu hình có {} ký tự (mật khẩu đang dùng: '{}'). Vui lòng kiểm tra lại Google Account!",
                        cleanedPassword.length(), cleanedPassword);
            }
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(smtpAuth && !username.isBlank()));
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        props.put("mail.smtp.ssl.enable", String.valueOf(ssl));
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        log.info("SMTP mail sender configured for host {}:{} (user: {})", host, port, username.isBlank() ? "anonymous" : username);
        return sender;
    }
}
