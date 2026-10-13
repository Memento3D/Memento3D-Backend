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
public class ZaloPayCallbackRequest {

    @JsonProperty("data")
    private String data;

    @JsonProperty("mac")
    private String mac;

    @JsonProperty("type")
    private int type;
}
