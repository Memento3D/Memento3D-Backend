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
public class ZaloPayQueryResponse {

    @JsonProperty("return_code")
    private int returnCode;

    @JsonProperty("return_message")
    private String returnMessage;

    @JsonProperty("sub_return_code")
    private int subReturnCode;

    @JsonProperty("sub_return_message")
    private String subReturnMessage;

    @JsonProperty("is_processing")
    private boolean isProcessing;

    @JsonProperty("amount")
    private long amount;

    @JsonProperty("zp_trans_id")
    private long zpTransId;

    @JsonProperty("server_time")
    private long serverTime;
}
