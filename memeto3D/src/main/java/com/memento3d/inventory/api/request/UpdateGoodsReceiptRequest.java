package com.memento3d.inventory.api.request;

import jakarta.validation.Valid;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateGoodsReceiptRequest {
    private String supplier;
    private String note;

    @Valid
    private List<CreateGoodsReceiptRequest.GoodsReceiptItemRequest> items;
}
