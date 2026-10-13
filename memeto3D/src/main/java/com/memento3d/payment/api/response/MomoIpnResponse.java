package com.memento3d.payment.api.response;

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
public class MomoIpnResponse {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private int resultCode;
    private String message;

    public static MomoIpnResponse success(String partnerCode, String orderId, String requestId) {
        return MomoIpnResponse.builder()
                .partnerCode(partnerCode)
                .orderId(orderId)
                .requestId(requestId)
                .resultCode(0)
                .message("success")
                .build();
    }
}
