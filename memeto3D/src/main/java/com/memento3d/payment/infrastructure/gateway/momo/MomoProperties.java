package com.memento3d.payment.infrastructure.gateway.momo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.payment.momo")
@Getter
@Setter
public class MomoProperties {
    private String partnerCode = "MOMO";
    private String accessKey = "F8BBA842ECF85";
    private String secretKey = "K951B6PE1wa8ngfBW/gahOD0viQDuMmq";
    private String endpoint = "https://test-payment.momo.vn/v2/gateway/api/create";
    private String queryEndpoint = "https://test-payment.momo.vn/v2/gateway/api/query";
    private String redirectUrl = "http://localhost:3000/payment/result";
    private String ipnUrl = "http://localhost:8080/api/payments/webhooks/momo";
    private String requestType = "captureWallet";
    private int timeoutSeconds = 30;
}
