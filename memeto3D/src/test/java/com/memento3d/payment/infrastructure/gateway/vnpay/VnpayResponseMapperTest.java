package com.memento3d.payment.infrastructure.gateway.vnpay;

import com.memento3d.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayResponseMapperTest {

    private VnpayResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new VnpayResponseMapper();
    }

    @Test
    @DisplayName("Should map code 00 to SUCCESS when transactionStatus is 00")
    void testMapSuccess() {
        PaymentStatus status = mapper.mapResponseCodeToStatus("00", "00");
        assertThat(status).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("Should map code 00 with transactionStatus 01 to PROCESSING")
    void testMapProcessing() {
        PaymentStatus status = mapper.mapResponseCodeToStatus("00", "01");
        assertThat(status).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    @DisplayName("Should map code 24 to CANCELLED")
    void testMapCancelled() {
        PaymentStatus status = mapper.mapResponseCodeToStatus("24", "02");
        assertThat(status).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should map code 11 to EXPIRED")
    void testMapExpired() {
        PaymentStatus status = mapper.mapResponseCodeToStatus("11", "02");
        assertThat(status).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    @DisplayName("Should map unknown or other error code to FAILED")
    void testMapFailed() {
        PaymentStatus status = mapper.mapResponseCodeToStatus("51", "02");
        assertThat(status).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("Should provide descriptive human-readable message for VNPAY codes")
    void testGetResponseMessage() {
        assertThat(mapper.getResponseMessage("00")).contains("thành công");
        assertThat(mapper.getResponseMessage("24")).contains("hủy");
        assertThat(mapper.getResponseMessage("11")).contains("hết hạn");
        assertThat(mapper.getResponseMessage("99")).contains("Các lỗi khác");
    }
}
