package com.memento3d.address.application.service.impl;

import com.memento3d.address.api.request.CreateAddressRequest;
import com.memento3d.address.api.request.UpdateAddressRequest;
import com.memento3d.address.api.response.AddressResponse;
import com.memento3d.address.application.service.AddressService;
import com.memento3d.address.domain.model.AddressLabel;
import com.memento3d.address.domain.model.UserAddress;
import com.memento3d.address.infrastructure.persistence.repository.UserAddressRepository;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.shared.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressServiceImpl implements AddressService {

    private final UserAddressRepository addressRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses(UUID userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId)
                .stream()
                .map(AddressResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse createAddress(UUID userId, CreateAddressRequest request) {
        addressRepository.lockOwner(userId);

        long count = addressRepository.countByUserId(userId);
        boolean makeDefault = count == 0 || Boolean.TRUE.equals(request.getIsDefault());

        if (makeDefault) {
            addressRepository.clearDefault(userId);
        }

        String normalizedPhone = PhoneNumbers.normalizeRequired(request.getPhone());

        UserAddress address = UserAddress.builder()
                .userId(userId)
                .recipientName(request.getRecipientName().trim())
                .phone(normalizedPhone)
                .province(request.getProvince().trim())
                .ward(request.getWard().trim())
                .streetAddress(request.getStreetAddress().trim())
                .label(request.getLabel() != null ? request.getLabel() : AddressLabel.HOME)
                .defaultAddress(makeDefault)
                .build();

        UserAddress saved = addressRepository.save(address);
        log.info("Created address {} for user {} (default={})", saved.getId(), userId, makeDefault);
        return AddressResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(UUID userId, UUID addressId, UpdateAddressRequest request) {
        UserAddress address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Địa chỉ không tồn tại hoặc bạn không có quyền truy cập."));

        if (request.getRecipientName() != null && !request.getRecipientName().isBlank()) {
            address.setRecipientName(request.getRecipientName().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            address.setPhone(PhoneNumbers.normalizeRequired(request.getPhone()));
        }
        if (request.getProvince() != null && !request.getProvince().isBlank()) {
            address.setProvince(request.getProvince().trim());
        }
        if (request.getWard() != null && !request.getWard().isBlank()) {
            address.setWard(request.getWard().trim());
        }
        if (request.getStreetAddress() != null && !request.getStreetAddress().isBlank()) {
            address.setStreetAddress(request.getStreetAddress().trim());
        }
        if (request.getLabel() != null) {
            address.setLabel(request.getLabel());
        }

        UserAddress saved = addressRepository.save(address);
        log.info("Updated address {} for user {}", addressId, userId);
        return AddressResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        addressRepository.lockOwner(userId);

        UserAddress address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Địa chỉ không tồn tại hoặc bạn không có quyền truy cập."));

        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        log.info("Deleted address {} for user {}", addressId, userId);

        // If we deleted the default address, automatically promote the most recently created remaining address to default
        if (wasDefault) {
            addressRepository.findFirstByUserIdAndIdNotOrderByCreatedAtDesc(userId, addressId)
                    .ifPresent(nextDefault -> {
                        nextDefault.setDefaultAddress(true);
                        addressRepository.save(nextDefault);
                        log.info("Promoted address {} to default for user {}", nextDefault.getId(), userId);
                    });
        }
    }

    @Override
    @Transactional
    public AddressResponse setDefaultAddress(UUID userId, UUID addressId) {
        addressRepository.lockOwner(userId);

        UserAddress address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Địa chỉ không tồn tại hoặc bạn không có quyền truy cập."));

        addressRepository.clearDefault(userId);
        address.setDefaultAddress(true);
        UserAddress saved = addressRepository.save(address);
        log.info("Set address {} as default for user {}", addressId, userId);
        return AddressResponse.fromEntity(saved);
    }
}
