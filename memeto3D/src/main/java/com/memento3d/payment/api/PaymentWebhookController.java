package com.memento3d.payment.api;

import com.memento3d.payment.api.response.MomoIpnResponse;
import com.memento3d.payment.api.response.ZaloPayCallbackResponse;
import com.memento3d.payment.application.service.PaymentService;
import com.memento3d.payment.infrastructure.gateway.momo.dto.MomoIpnRequest;
import com.memento3d.payment.infrastructure.gateway.zalopay.dto.ZaloPayCallbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/webhooks/payments", "/api/payments/webhooks"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Webhooks", description = "Public webhook receiver endpoints for payment gateway callbacks (MoMo, ZaloPay)")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/momo")
    @Operation(summary = "MoMo IPN Webhook", description = "Receives asynchronous IPN payment notifications from MoMo Payment Gateway")
    public ResponseEntity<MomoIpnResponse> handleMomoIpn(@RequestBody MomoIpnRequest request) {
        log.info("Received MoMo IPN webhook callback for orderId: {}", request.getOrderId());
        MomoIpnResponse response = paymentService.processMomoIpn(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/zalopay")
    @Operation(summary = "ZaloPay Callback Webhook", description = "Receives asynchronous callback notifications from ZaloPay Gateway")
    public ResponseEntity<ZaloPayCallbackResponse> handleZaloPayCallback(@RequestBody ZaloPayCallbackRequest request) {
        log.info("Received ZaloPay callback webhook");
        ZaloPayCallbackResponse response = paymentService.processZaloPayCallback(request);
        return ResponseEntity.ok(response);
    }
}
