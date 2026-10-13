package com.memento3d.payment.api;

import com.memento3d.payment.api.response.MomoIpnResponse;
import com.memento3d.payment.api.response.ZaloPayCallbackResponse;
import com.memento3d.payment.application.service.PaymentService;
import com.memento3d.payment.infrastructure.gateway.momo.dto.MomoIpnRequest;
import com.memento3d.payment.infrastructure.gateway.zalopay.dto.ZaloPayCallbackRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentWebhookController webhookController;

    @Test
    @DisplayName("MoMo IPN webhook returns MoMo acknowledgment")
    void testHandleMomoIpn() {
        MomoIpnRequest request = MomoIpnRequest.builder()
                .partnerCode("MOMO")
                .orderId("MM_123")
                .requestId("REQ_123")
                .amount(50000L)
                .resultCode(0)
                .build();

        MomoIpnResponse mockResponse = MomoIpnResponse.success("MOMO", "MM_123", "REQ_123");
        when(paymentService.processMomoIpn(any(MomoIpnRequest.class))).thenReturn(mockResponse);

        ResponseEntity<MomoIpnResponse> response = webhookController.handleMomoIpn(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getResultCode());
        verify(paymentService).processMomoIpn(request);
    }

    @Test
    @DisplayName("ZaloPay callback webhook returns ZaloPay acknowledgment")
    void testHandleZaloPayCallback() {
        ZaloPayCallbackRequest request = ZaloPayCallbackRequest.builder()
                .data("{\"app_id\":2553}")
                .mac("mac123")
                .build();

        ZaloPayCallbackResponse mockResponse = ZaloPayCallbackResponse.success();
        when(paymentService.processZaloPayCallback(any(ZaloPayCallbackRequest.class))).thenReturn(mockResponse);

        ResponseEntity<ZaloPayCallbackResponse> response = webhookController.handleZaloPayCallback(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getReturnCode());
        verify(paymentService).processZaloPayCallback(request);
    }
}
