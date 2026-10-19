package com.memento3d.address.application.service;

import com.memento3d.address.api.request.CreateAddressRequest;
import com.memento3d.address.api.request.UpdateAddressRequest;
import com.memento3d.address.api.response.AddressResponse;

import java.util.List;
import java.util.UUID;

public interface AddressService {
    List<AddressResponse> getMyAddresses(UUID userId);
    AddressResponse createAddress(UUID userId, CreateAddressRequest request);
    AddressResponse updateAddress(UUID userId, UUID addressId, UpdateAddressRequest request);
    void deleteAddress(UUID userId, UUID addressId);
    AddressResponse setDefaultAddress(UUID userId, UUID addressId);
}
