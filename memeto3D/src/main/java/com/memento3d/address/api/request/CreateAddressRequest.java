package com.memento3d.address.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.memento3d.address.domain.model.AddressLabel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(description = "Create an address in the authenticated user's address book")
public class CreateAddressRequest {

    @NotBlank(message = "Vui lòng nhập tên người nhận")
    @Size(max = 100, message = "Tên người nhận tối đa 100 ký tự")
    private String recipientName;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Size(max = 30, message = "Số điện thoại quá dài")
    @Schema(example = "0912345678")
    private String phone;

    @NotBlank(message = "Vui lòng nhập Tỉnh/Thành phố")
    @Size(max = 100, message = "Tỉnh/Thành phố tối đa 100 ký tự")
    private String province;

    @NotBlank(message = "Vui lòng nhập Phường/Xã")
    @Size(max = 100, message = "Phường/Xã tối đa 100 ký tự")
    private String ward;

    @NotBlank(message = "Vui lòng nhập địa chỉ chi tiết")
    @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
    private String streetAddress;

    @Schema(description = "HOME, OFFICE or OTHER (defaults to HOME)")
    private AddressLabel label;

    @Schema(description = "Make this the default address")
    private Boolean isDefault;
}
