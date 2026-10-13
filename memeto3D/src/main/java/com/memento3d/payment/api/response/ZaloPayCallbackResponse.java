package com.memento3d.payment.api.response;

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
public class ZaloPayCallbackResponse {

    @JsonProperty("return_code")
    private int returnCode;

    @JsonProperty("return_message")
    private String returnMessage;

    public static ZaloPayCallbackResponse success() {
        return new ZaloPayCallbackResponse(1, "success");
    }

    public static ZaloPayCallbackResponse fail(String message) {
        return new ZaloPayCallbackResponse(2, message);
    }
}
