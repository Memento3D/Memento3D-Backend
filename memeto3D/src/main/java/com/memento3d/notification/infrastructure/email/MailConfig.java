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
            sender.setUsername(username);
            sender.setPassword(password);
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
