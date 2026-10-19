package com.memento3d.address.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.memento3d.address.domain.model.AddressLabel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PATCH semantics: null fields are untouched. The default flag is changed only through
 * {@code PATCH /addresses/{id}/default} (atomic switch), never here.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(description = "Partial update of an address")
public class UpdateAddressRequest {

    @Size(max = 100, message = "Tên người nhận tối đa 100 ký tự")
    private String recipientName;

    @Size(max = 30, message = "Số điện thoại quá dài")
    private String phone;

    @Size(max = 100, message = "Tỉnh/Thành phố tối đa 100 ký tự")
    private String province;

    @Size(max = 100, message = "Phường/Xã tối đa 100 ký tự")
    private String ward;

    @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
    private String streetAddress;

    private AddressLabel label;
}
