package com.memento3d.payment.infrastructure.gateway.zalopay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.payment.application.dto.GatewayPaymentRequest;
import com.memento3d.payment.application.dto.GatewayPaymentResponse;
import com.memento3d.payment.application.dto.GatewayQueryResponse;
import com.memento3d.payment.domain.exception.PaymentGatewayException;
import com.memento3d.payment.domain.gateway.PaymentGateway;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import com.memento3d.payment.infrastructure.gateway.zalopay.dto.ZaloPayCreateResponse;
import com.memento3d.payment.infrastructure.gateway.zalopay.dto.ZaloPayQueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ZaloPayPaymentGateway implements PaymentGateway {

    private final ZaloPayProperties properties;
    private final ZaloPaySignatureService signatureService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ZaloPayPaymentGateway(ZaloPayProperties properties, ZaloPaySignatureService signatureService) {
        this.properties = properties;
        this.signatureService = signatureService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.ZALOPAY;
    }

    @Override
    public GatewayPaymentResponse createPayment(PaymentTransaction transaction, GatewayPaymentRequest request) {
        try {
            int appId = properties.getAppId();
            String appTransId = transaction.getMerchantTransactionId();
            String appUser = transaction.getUserId().toString();
            long amount = transaction.getAmount().longValue();
            long appTime = System.currentTimeMillis();

            String redirectUrl = (request != null && request.getRedirectUrl() != null && !request.getRedirectUrl().isBlank())
                    ? request.getRedirectUrl()
                    : properties.getRedirectUrl();

            Map<String, Object> embedDataMap = new HashMap<>();
            embedDataMap.put("redirecturl", redirectUrl);
            String embedData = objectMapper.writeValueAsString(embedDataMap);
            String item = "[]";
            String description = (request != null && request.getOrderInfo() != null && !request.getOrderInfo().isBlank())
                    ? request.getOrderInfo()
                    : "Payment for order " + transaction.getOrderId();
            String callbackUrl = properties.getCallbackUrl();

            String mac = signatureService.generateCreateMac(
                    appId,
                    appTransId,
                    appUser,
                    amount,
                    appTime,
                    embedData,
                    item,
                    properties.getKey1()
            );

            Map<String, String> formParams = new HashMap<>();
            formParams.put("app_id", String.valueOf(appId));
            formParams.put("app_user", appUser);
            formParams.put("app_trans_id", appTransId);
            formParams.put("app_time", String.valueOf(appTime));
            formParams.put("amount", String.valueOf(amount));
            formParams.put("item", item);
            formParams.put("description", description);
            formParams.put("embed_data", embedData);
            formParams.put("callback_url", callbackUrl);
            formParams.put("mac", mac);

            String requestBody = formParams.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            log.info("Sending ZaloPay create order request for app_trans_id: {}", appTransId);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getEndpoint()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            log.info("Received ZaloPay create order response. Status code: {}", httpResponse.statusCode());

            if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                ZaloPayCreateResponse response = objectMapper.readValue(httpResponse.body(), ZaloPayCreateResponse.class);
                if (response.getReturnCode() == 1) {
                    return GatewayPaymentResponse.builder()
                            .success(true)
                            .paymentUrl(response.getOrderUrl())
                            .providerTransactionId(response.getZpTransToken())
                            .responseCode(String.valueOf(response.getReturnCode()))
                            .message(response.getReturnMessage())
                            .build();
                } else {
                    log.warn("ZaloPay create order failed with return_code {}: {}", response.getReturnCode(), response.getReturnMessage());
                    return GatewayPaymentResponse.builder()
                            .success(false)
                            .responseCode(String.valueOf(response.getReturnCode()))
                            .message(response.getReturnMessage())
                            .build();
                }
            } else {
                throw new PaymentGatewayException("ZaloPay responded with HTTP status " + httpResponse.statusCode());
            }
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute ZaloPay create order for app_trans_id: {}", transaction.getMerchantTransactionId(), e);
            throw new PaymentGatewayException("Failed to call ZaloPay create order API: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayQueryResponse queryPayment(PaymentTransaction transaction) {
        try {
            int appId = properties.getAppId();
            String appTransId = transaction.getMerchantTransactionId();
            String mac = signatureService.generateQueryMac(appId, appTransId, properties.getKey1());

            Map<String, String> formParams = new HashMap<>();
            formParams.put("app_id", String.valueOf(appId));
            formParams.put("app_trans_id", appTransId);
            formParams.put("mac", mac);

            String requestBody = formParams.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            log.info("Sending ZaloPay query order request for app_trans_id: {}", appTransId);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getQueryEndpoint()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                ZaloPayQueryResponse response = objectMapper.readValue(httpResponse.body(), ZaloPayQueryResponse.class);
                PaymentStatus status;
                if (response.getReturnCode() == 1) {
                    status = PaymentStatus.SUCCESS;
                } else if (response.getReturnCode() == 3 || response.isProcessing()) {
                    status = PaymentStatus.PROCESSING;
                } else {
                    status = PaymentStatus.FAILED;
                }

                return GatewayQueryResponse.builder()
                        .status(status)
                        .providerTransactionId(response.getZpTransId() > 0 ? String.valueOf(response.getZpTransId()) : null)
                        .amount(java.math.BigDecimal.valueOf(response.getAmount()))
                        .responseCode(String.valueOf(response.getReturnCode()))
                        .message(response.getReturnMessage())
                        .paidAt(status == PaymentStatus.SUCCESS ? Instant.now() : null)
                        .build();
            } else {
                throw new PaymentGatewayException("ZaloPay query responded with HTTP status " + httpResponse.statusCode());
            }
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute ZaloPay query for app_trans_id: {}", transaction.getMerchantTransactionId(), e);
            throw new PaymentGatewayException("Failed to call ZaloPay query API: " + e.getMessage(), e);
        }
    }
}
