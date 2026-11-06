package com.memento3d.customization.api;
import com.memento3d.customization.application.service.PrintDesignService;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor @PreAuthorize("isAuthenticated()") @SecurityRequirement(name="BearerAuth")
@RequestMapping({"/api/v1/orders/{orderId}/custom-design","/api/orders/{orderId}/custom-design"})
public class CustomPrintDesignController {
    private final PrintDesignService designs;
    public record Review(@NotNull Boolean approved,@NotBlank @Size(min=10,max=4000) String notes,
        @AssertTrue(message="Confirm geometry, accessory fit, requested scale and manufacturing checks") boolean manufacturingChecked) {}
    @GetMapping public ResponseEntity<PrintDesignService.View> get(@PathVariable UUID orderId,@AuthenticationPrincipal CustomUserDetails user) {
        return response(designs.view(orderId,user.getId(),user.getRole()));
    }
    @PostMapping("/retry") public ResponseEntity<PrintDesignService.View> retry(@PathVariable UUID orderId,@AuthenticationPrincipal CustomUserDetails user) {
        return response(designs.retry(orderId,user.getId(),user.getRole()));
    }
    @PostMapping("/review") @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPERATOR')")
    public ResponseEntity<PrintDesignService.View> review(@PathVariable UUID orderId,@Valid @RequestBody Review request,@AuthenticationPrincipal CustomUserDetails user) {
        return response(designs.review(orderId,request.approved(),request.notes(),user.getId(),user.getRole()));
    }
    private <T> ResponseEntity<T> response(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
