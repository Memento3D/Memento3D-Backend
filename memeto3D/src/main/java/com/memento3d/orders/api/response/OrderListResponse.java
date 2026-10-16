package com.memento3d.orders.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Paginated list of 3D print orders")
public class OrderListResponse {

    @Schema(description = "List of orders")
    private List<OrderResponse> orders;

    @Schema(description = "Current page number (1-based)", example = "1")
    private Integer page;

    @Schema(description = "Page size limit", example = "20")
    private Integer limit;

    @Schema(description = "Total number of orders", example = "42")
    private Long total;
}
