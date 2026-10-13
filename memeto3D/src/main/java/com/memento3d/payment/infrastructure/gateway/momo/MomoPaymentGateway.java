package com.memento3d.payment.infrastructure.gateway.momo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.payment.application.dto.GatewayPaymentRequest;
import com.memento3d.payment.application.dto.GatewayPaymentResponse;
import com.memento3d.payment.application.dto.GatewayQueryResponse;
import com.memento3d.payment.domain.exception.PaymentGatewayException;
import com.memento3d.payment.domain.gateway.PaymentGateway;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import com.memento3d.payment.infrastructure.gateway.momo.dto.MomoCreateRequest;
import com.memento3d.payment.infrastructure.gateway.momo.dto.MomoCreateResponse;
import com.memento3d.payment.infrastructure.gateway.momo.dto.MomoQueryRequest;
import com.memento3d.payment.infrastructure.gateway.momo.dto.MomoQueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class MomoPaymentGateway implements PaymentGateway {

    private final MomoProperties properties;
    private final MomoSignatureService signatureService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MomoPaymentGateway(MomoProperties properties, MomoSignatureService signatureService) {
        this.properties = properties;
        this.signatureService = signatureService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.MOMO;
    }

    @Override
    public GatewayPaymentResponse createPayment(PaymentTransaction transaction, GatewayPaymentRequest request) {
        try {
            long amount = transaction.getAmount().longValue();
            String orderId = transaction.getMerchantTransactionId();
            String requestId = transaction.getRequestId();
            String orderInfo = (request != null && request.getOrderInfo() != null && !request.getOrderInfo().isBlank())
                    ? request.getOrderInfo()
                    : "Payment for order " + transaction.getOrderId();
            String redirectUrl = (request != null && request.getRedirectUrl() != null && !request.getRedirectUrl().isBlank())
                    ? request.getRedirectUrl()
                    : properties.getRedirectUrl();
            String ipnUrl = properties.getIpnUrl();
            String requestType = properties.getRequestType();
            String extraData = (request != null && request.getExtraData() != null) ? request.getExtraData() : "";

            String signature = signatureService.signCreateRequest(
                    properties.getAccessKey(),
                    amount,
                    extraData,
                    ipnUrl,
                    orderId,
                    orderInfo,
                    properties.getPartnerCode(),
                    redirectUrl,
                    requestId,
                    requestType,
                    properties.getSecretKey()
            );

            MomoCreateRequest createRequest = MomoCreateRequest.builder()
                    .partnerCode(properties.getPartnerCode())
                    .requestId(requestId)
                    .amount(amount)
                    .orderId(orderId)
                    .orderInfo(orderInfo)
                    .redirectUrl(redirectUrl)
                    .ipnUrl(ipnUrl)
                    .requestType(requestType)
                    .extraData(extraData)
                    .lang("vi")
                    .signature(signature)
                    .build();

            String requestBody = objectMapper.writeValueAsString(createRequest);
            log.info("Sending MoMo create payment request for orderId: {}, requestId: {}", orderId, requestId);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getEndpoint()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            log.info("Received MoMo create payment response. Status code: {}", httpResponse.statusCode());

            if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                MomoCreateResponse response = objectMapper.readValue(httpResponse.body(), MomoCreateResponse.class);
                if (response.getResultCode() == 0) {
                    return GatewayPaymentResponse.builder()
                            .success(true)
                            .paymentUrl(response.getPayUrl())
                            .qrCodeUrl(response.getQrCodeUrl())
                            .deeplink(response.getDeeplink())
                            .responseCode(String.valueOf(response.getResultCode()))
                            .message(response.getMessage())
                            .build();
                } else {
                    log.warn("MoMo create payment failed with resultCode {}: {}", response.getResultCode(), response.getMessage());
                    return GatewayPaymentResponse.builder()
                            .success(false)
                            .responseCode(String.valueOf(response.getResultCode()))
                            .message(response.getMessage())
                            .build();
                }
            } else {
                log.error("MoMo HTTP call failed with status: {}, body: {}", httpResponse.statusCode(), httpResponse.body());
                throw new PaymentGatewayException("MoMo gateway responded with HTTP status " + httpResponse.statusCode());
            }
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute MoMo create payment for orderId: {}", transaction.getMerchantTransactionId(), e);
            throw new PaymentGatewayException("Failed to call MoMo create payment API: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayQueryResponse queryPayment(PaymentTransaction transaction) {
        try {
            String orderId = transaction.getMerchantTransactionId();
            String requestId = transaction.getRequestId();

            String signature = signatureService.signQueryRequest(
                    properties.getAccessKey(),
                    orderId,
                    properties.getPartnerCode(),
                    requestId,
                    properties.getSecretKey()
            );

            MomoQueryRequest queryRequest = MomoQueryRequest.builder()
                    .partnerCode(properties.getPartnerCode())
                    .orderId(orderId)
                    .requestId(requestId)
                    .signature(signature)
                    .lang("vi")
                    .build();

            String requestBody = objectMapper.writeValueAsString(queryRequest);
            log.info("Sending MoMo query payment request for orderId: {}, requestId: {}", orderId, requestId);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getQueryEndpoint()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                MomoQueryResponse response = objectMapper.readValue(httpResponse.body(), MomoQueryResponse.class);
                PaymentStatus status;
                if (response.getResultCode() == 0) {
                    status = PaymentStatus.SUCCESS;
                } else if (response.getResultCode() == 9000 || response.getResultCode() == 1000) {
                    status = PaymentStatus.PROCESSING;
                } else {
                    status = PaymentStatus.FAILED;
                }

                return GatewayQueryResponse.builder()
                        .status(status)
                        .providerTransactionId(response.getTransId() > 0 ? String.valueOf(response.getTransId()) : null)
                        .amount(java.math.BigDecimal.valueOf(response.getAmount()))
                        .responseCode(String.valueOf(response.getResultCode()))
                        .message(response.getMessage())
                        .paidAt(status == PaymentStatus.SUCCESS ? Instant.now() : null)
                        .build();
            } else {
                throw new PaymentGatewayException("MoMo query API responded with HTTP status " + httpResponse.statusCode());
            }
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to query MoMo transaction for orderId: {}", transaction.getMerchantTransactionId(), e);
            throw new PaymentGatewayException("Failed to call MoMo query API: " + e.getMessage(), e);
        }
    }
}
