package com.memento3d.payment.infrastructure.gateway.zalopay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.payment.zalopay")
@Getter
@Setter
public class ZaloPayProperties {
    private int appId = 2553;
    private String key1 = "PcY4iZIKFCIdgZvA6ueMcMHHUbRLYjPL";
    private String key2 = "kLtgPl8HHhfvMuD2wKfgccM4nwqiEdtF";
    private String endpoint = "https://sb-openapi.zalopay.vn/v2/create";
    private String queryEndpoint = "https://sb-openapi.zalopay.vn/v2/query";
    private String callbackUrl = "http://localhost:8080/api/payments/webhooks/zalopay";
    private String redirectUrl = "http://localhost:3000/payment/result";
    private int timeoutSeconds = 30;
}
