package com.memento3d.payment.domain.gateway;

import com.memento3d.payment.domain.exception.PaymentException;
import com.memento3d.payment.domain.model.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentGatewayFactory {

    private final Map<PaymentMethod, PaymentGateway> gateways = new EnumMap<>(PaymentMethod.class);

    public PaymentGatewayFactory(List<PaymentGateway> gatewayList) {
        for (PaymentGateway gateway : gatewayList) {
            gateways.put(gateway.supports(), gateway);
        }
    }

    public PaymentGateway getGateway(PaymentMethod method) {
        PaymentGateway gateway = gateways.get(method);
        if (gateway == null) {
            throw new PaymentException("Unsupported payment method: " + method);
        }
        return gateway;
    }
}
