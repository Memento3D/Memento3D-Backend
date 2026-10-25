package com.memento3d.payment.infrastructure.gateway.vnpay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Standard VNPAY IPN webhook response format")
public class VnpayIpnResponse {

    @JsonProperty("RspCode")
    @Schema(description = "Response code: 00 (Success), 01 (Not found), 02 (Already confirmed), 04 (Invalid amount), 97 (Checksum fail)", example = "00")
    private String rspCode;

    @JsonProperty("Message")
    @Schema(description = "Response message", example = "Confirm Success")
    private String message;

    public static VnpayIpnResponse success() {
        return new VnpayIpnResponse("00", "Confirm Success");
    }

    public static VnpayIpnResponse orderNotFound() {
        return new VnpayIpnResponse("01", "Order not found");
    }

    public static VnpayIpnResponse alreadyConfirmed() {
        return new VnpayIpnResponse("02", "Order already confirmed");
    }

    public static VnpayIpnResponse invalidAmount() {
        return new VnpayIpnResponse("04", "Invalid amount");
    }

    public static VnpayIpnResponse invalidChecksum() {
        return new VnpayIpnResponse("97", "Invalid Checksum");
    }

    public static VnpayIpnResponse unknownError(String msg) {
        return new VnpayIpnResponse("99", msg != null ? msg : "Unknown error");
    }
}
