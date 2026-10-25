package com.memento3d.payment.infrastructure.gateway.vnpay;

import com.memento3d.payment.domain.model.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class VnpayResponseMapper {

    private static final Map<String, String> RESPONSE_MESSAGES = Map.ofEntries(
            Map.entry("00", "Giao dịch thành công"),
            Map.entry("07", "Trừ tiền thành công. Giao dịch bị nghi ngờ gian lận"),
            Map.entry("09", "Thẻ/Tài khoản chưa đăng ký dịch vụ InternetBanking tại ngân hàng"),
            Map.entry("10", "Khách hàng xác thực thông tin thẻ/tài khoản không đúng quá 3 lần"),
            Map.entry("11", "Đã hết hạn chờ thanh toán"),
            Map.entry("12", "Thẻ/Tài khoản của khách hàng bị khóa"),
            Map.entry("13", "Quý khách nhập sai mật khẩu xác thực giao dịch (OTP)"),
            Map.entry("24", "Khách hàng hủy giao dịch"),
            Map.entry("51", "Tài khoản của quý khách không đủ số dư để thực hiện giao dịch"),
            Map.entry("65", "Tài khoản của Quý khách đã vượt quá hạn mức giao dịch trong ngày"),
            Map.entry("75", "Ngân hàng thanh toán đang bảo trì"),
            Map.entry("79", "Khách hàng nhập sai mật khẩu thanh toán quá số lần quy định"),
            Map.entry("99", "Các lỗi khác")
    );

    /**
     * Maps VNPAY vnp_ResponseCode to internal PaymentStatus.
     */
    public PaymentStatus mapResponseCodeToStatus(String responseCode, String transactionStatus) {
        if (responseCode == null) {
            return PaymentStatus.FAILED;
        }

        // Both responseCode and transactionStatus must be "00" for SUCCESS
        if ("00".equals(responseCode)) {
            if (transactionStatus == null || "00".equals(transactionStatus)) {
                return PaymentStatus.SUCCESS;
            } else if ("01".equals(transactionStatus)) {
                return PaymentStatus.PROCESSING;
            } else {
                return PaymentStatus.FAILED;
            }
        }

        return switch (responseCode) {
            case "24" -> PaymentStatus.CANCELLED;
            case "11" -> PaymentStatus.EXPIRED;
            default -> PaymentStatus.FAILED;
        };
    }

    /**
     * Returns human-readable Vietnamese description for VNPAY response code.
     */
    public String getResponseMessage(String responseCode) {
        if (responseCode == null) {
            return "Mã phản hồi không xác định";
        }
        return RESPONSE_MESSAGES.getOrDefault(responseCode, "Giao dịch thất bại (Mã lỗi VNPAY: " + responseCode + ")");
    }
}
