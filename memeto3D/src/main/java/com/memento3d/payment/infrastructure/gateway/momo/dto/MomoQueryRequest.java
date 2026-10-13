package com.memento3d.payment.infrastructure.gateway.momo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MomoQueryRequest {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private String signature;
    private String lang;
}
