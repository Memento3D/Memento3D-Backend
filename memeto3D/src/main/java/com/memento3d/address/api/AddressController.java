package com.memento3d.address.api;

import com.memento3d.address.api.request.CreateAddressRequest;
import com.memento3d.address.api.request.UpdateAddressRequest;
import com.memento3d.address.api.response.AddressResponse;
import com.memento3d.address.application.service.AddressService;
import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.shared.api.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
@Tag(name = "Address Management", description = "Personal address book for the authenticated user")
@SecurityRequirement(name = "BearerAuth")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @Operation(summary = "Get user's address book", description = "Returns addresses of authenticated user, default address first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of addresses",
                    content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AddressResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<AddressResponse>> getMyAddresses(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(addressService.getMyAddresses(requireUserId(principal)));
    }

    @PostMapping
    @Operation(summary = "Add a new address", description = "Creates a new delivery address in the user's address book")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Address created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AddressResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AddressResponse> createAddress(
            @Valid @RequestBody CreateAddressRequest request,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        AddressResponse response = addressService.createAddress(requireUserId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update address", description = "Partially updates an address belonging to the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Address updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AddressResponse.class))),
            @ApiResponse(responseCode = "404", description = "Address not found or not owned by user",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AddressResponse> updateAddress(
            @PathVariable("id") UUID addressId,
            @Valid @RequestBody UpdateAddressRequest request,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(addressService.updateAddress(requireUserId(principal), addressId, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete address", description = "Deletes an address from the user's address book. If default was deleted, another is promoted.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Address deleted"),
            @ApiResponse(responseCode = "404", description = "Address not found or not owned by user",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteAddress(
            @PathVariable("id") UUID addressId,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        addressService.deleteAddress(requireUserId(principal), addressId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/default")
    @Operation(summary = "Set address as default", description = "Atomically marks this address as default and unmarks the previous default")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Address marked as default",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AddressResponse.class))),
            @ApiResponse(responseCode = "404", description = "Address not found or not owned by user",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AddressResponse> setDefaultAddress(
            @PathVariable("id") UUID addressId,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(addressService.setDefaultAddress(requireUserId(principal), addressId));
    }

    private UUID requireUserId(CustomUserDetails principal) {
        if (principal == null || principal.getId() == null) {
            throw new InvalidTokenException("No authenticated user in security context");
        }
        return principal.getId();
    }
}
