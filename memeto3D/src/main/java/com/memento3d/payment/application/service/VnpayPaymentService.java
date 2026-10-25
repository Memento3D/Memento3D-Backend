package com.memento3d.payment.application.service;

import com.memento3d.payment.infrastructure.gateway.vnpay.dto.CreateVnpayPaymentRequest;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.CreateVnpayPaymentResponse;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.VnpayIpnResponse;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.VnpayReturnResponse;

import java.util.Map;
import java.util.UUID;

public interface VnpayPaymentService {

    /**
     * Creates a new VNPAY payment transaction and generates the signed payment redirect URL.
     * Enforces ownership, price authoritativeness (never trusts frontend amount), and payment eligibility.
     */
    CreateVnpayPaymentResponse createPayment(CreateVnpayPaymentRequest request, UUID userId, String clientIp);

    /**
     * Verifies the customer return redirect from VNPAY, reconciles transaction, and returns result DTO.
     */
    VnpayReturnResponse processReturn(Map<String, String> queryParams);

    /**
     * Server-to-server asynchronous IPN webhook handler from VNPAY. Idempotent and concurrency-safe.
     */
    VnpayIpnResponse processIpn(Map<String, String> queryParams);
}
