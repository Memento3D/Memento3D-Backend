package com.memento3d.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayPaymentResponse {
    private boolean success;
    private String paymentUrl;
    private String qrCodeUrl;
    private String deeplink;
    private String providerTransactionId;
    private String message;
    private String responseCode;
}
