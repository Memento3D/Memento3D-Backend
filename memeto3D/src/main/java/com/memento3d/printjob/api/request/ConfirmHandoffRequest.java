package com.memento3d.printjob.api.request;

import com.memento3d.printjob.domain.model.ResinHandoffMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ConfirmHandoffRequest {

    @NotNull(message = "Phương thức bàn giao không được để trống (USB_MANUAL hoặc CHITU_MANAGER_LAN_MANUAL)")
    private ResinHandoffMethod handoffMethod;

    private UUID printerId;
}
