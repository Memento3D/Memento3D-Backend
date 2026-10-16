package com.memento3d.payment.application.service;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.payment.api.request.CreatePaymentRequest;
import com.memento3d.payment.api.response.MomoIpnResponse;
import com.memento3d.payment.api.response.PaymentResponse;
import com.memento3d.payment.api.response.PaymentStatusResponse;
import com.memento3d.payment.api.response.ZaloPayCallbackResponse;
import com.memento3d.payment.infrastructure.gateway.momo.dto.MomoIpnRequest;
import com.memento3d.payment.infrastructure.gateway.zalopay.dto.ZaloPayCallbackRequest;

import java.util.UUID;

public interface PaymentService {
    PaymentResponse createPayment(CreatePaymentRequest request, UUID userId);
    PaymentStatusResponse getPaymentStatus(UUID paymentId, UUID userId, Role role);
    PaymentStatusResponse cancelPayment(UUID paymentId, UUID userId, Role role);
    MomoIpnResponse processMomoIpn(MomoIpnRequest ipnRequest);
    ZaloPayCallbackResponse processZaloPayCallback(ZaloPayCallbackRequest callbackRequest);
}
