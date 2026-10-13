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
public class MomoQueryResponse {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private String extraData;
    private long amount;
    private long transId;
    private String payType;
    private int resultCode;
    private String message;
    private long responseTime;
}
