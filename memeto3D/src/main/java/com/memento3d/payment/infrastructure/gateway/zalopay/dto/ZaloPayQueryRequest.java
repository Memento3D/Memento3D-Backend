package com.memento3d.payment.infrastructure.gateway.zalopay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ZaloPayQueryRequest {

    @JsonProperty("app_id")
    private int appId;

    @JsonProperty("app_trans_id")
    private String appTransId;

    @JsonProperty("mac")
    private String mac;
}
