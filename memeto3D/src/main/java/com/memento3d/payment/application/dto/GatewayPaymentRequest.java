package com.memento3d.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayPaymentRequest {
    private String orderInfo;
    private String redirectUrl;
    private String extraData;
    private Map<String, Object> customParams;
}
