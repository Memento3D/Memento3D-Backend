package com.memento3d.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Paginated list of users response")
public class UserListResponse {

    @Schema(description = "List of user profiles")
    private List<UserResponse> users;

    @Schema(description = "Current page number (1-based)", example = "1")
    private Integer page;

    @Schema(description = "Page size limit", example = "20")
    private Integer limit;

    @Schema(description = "Total number of users found", example = "150")
    private Long total;
}
