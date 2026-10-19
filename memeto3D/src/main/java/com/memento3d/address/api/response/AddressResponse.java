package com.memento3d.address.api.response;

import com.memento3d.address.domain.model.AddressLabel;
import com.memento3d.address.domain.model.UserAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Saved address")
public class AddressResponse {

    private UUID id;
    private String recipientName;
    private String phone;
    private String province;
    private String ward;
    private String streetAddress;
    private AddressLabel label;
    private Boolean isDefault;

    @Schema(description = "Single-line address ready to be snapshotted into an order", example = "123 Nguyễn Trãi, Phường Bến Thành, TP. Hồ Chí Minh")
    private String formattedAddress;

    private Instant createdAt;
    private Instant updatedAt;

    public static AddressResponse fromEntity(UserAddress a) {
        return AddressResponse.builder()
                .id(a.getId())
                .recipientName(a.getRecipientName())
                .phone(a.getPhone())
                .province(a.getProvince())
                .ward(a.getWard())
                .streetAddress(a.getStreetAddress())
                .label(a.getLabel())
                .isDefault(a.isDefaultAddress())
                .formattedAddress(a.formatted())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
