package com.memento3d.payment.domain.gateway;

import com.memento3d.payment.application.dto.GatewayPaymentRequest;
import com.memento3d.payment.application.dto.GatewayPaymentResponse;
import com.memento3d.payment.application.dto.GatewayQueryResponse;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentTransaction;

public interface PaymentGateway {
    PaymentMethod supports();
    GatewayPaymentResponse createPayment(PaymentTransaction transaction, GatewayPaymentRequest request);
    GatewayQueryResponse queryPayment(PaymentTransaction transaction);
}
