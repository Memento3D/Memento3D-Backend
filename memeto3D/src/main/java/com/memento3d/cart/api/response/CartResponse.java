package com.memento3d.cart.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {

    @Builder.Default
    private List<CartItemResponse> items = new ArrayList<>();

    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Builder.Default
    private int totalItems = 0;
}
