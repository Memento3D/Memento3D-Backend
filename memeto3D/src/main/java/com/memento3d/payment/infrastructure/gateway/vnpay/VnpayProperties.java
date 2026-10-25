package com.memento3d.payment.infrastructure.gateway.vnpay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "payment.vnpay")
@Getter
@Setter
public class VnpayProperties {

    /**
     * VNPAY Terminal / Merchant Code (vnp_TmnCode)
     */
    private String tmnCode = "";

    /**
     * VNPAY Secret Key used for HMAC-SHA512 signature computation
     */
    private String hashSecret = "";

    /**
     * VNPAY Payment gateway entrypoint URL (Sandbox: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html)
     */
    private String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    /**
     * Frontend return redirect URL after customer pays on VNPAY
     */
    private String returnUrl = "http://localhost:5173/payment/vnpay/return";

    /**
     * Backend server-to-server IPN webhook notification URL
     */
    private String ipnUrl = "http://localhost:8080/api/v1/payments/vnpay/ipn";

    /**
     * VNPAY API specification version (2.1.0)
     */
    private String version = "2.1.0";

    /**
     * Payment command ('pay')
     */
    private String command = "pay";

    /**
     * Order category/type ('other')
     */
    private String orderType = "other";

    /**
     * Transaction currency code ('VND')
     */
    private String currCode = "VND";

    /**
     * Interface locale ('vn' or 'en')
     */
    private String locale = "vn";

    /**
     * Transaction payment timeout in minutes (default 15 minutes)
     */
    private int expireMinutes = 15;
}
