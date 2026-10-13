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
public class ZaloPayCreateRequest {

    @JsonProperty("app_id")
    private int appId;

    @JsonProperty("app_user")
    private String appUser;

    @JsonProperty("app_trans_id")
    private String appTransId;

    @JsonProperty("app_time")
    private long appTime;

    @JsonProperty("amount")
    private long amount;

    @JsonProperty("item")
    private String item;

    @JsonProperty("description")
    private String description;

    @JsonProperty("embed_data")
    private String embedData;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @JsonProperty("mac")
    private String mac;
}
