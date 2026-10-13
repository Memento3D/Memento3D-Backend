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
public class MomoIpnRequest {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private long amount;
    private String orderInfo;
    private String orderType;
    private long transId;
    private int resultCode;
    private String message;
    private String payType;
    private long responseTime;
    private String extraData;
    private String signature;
}
