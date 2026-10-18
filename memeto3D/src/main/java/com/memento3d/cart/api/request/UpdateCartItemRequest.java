package com.memento3d.cart.api.request;

import jakarta.validation.constraints.Min;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCartItemRequest {

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;
}
