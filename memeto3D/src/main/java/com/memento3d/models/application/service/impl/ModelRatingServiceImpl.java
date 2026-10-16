package com.memento3d.models.application.service.impl;

import com.memento3d.models.api.request.CreateModelRatingRequest;
import com.memento3d.models.api.response.ModelRatingResponse;
import com.memento3d.models.domain.model.ModelRating;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.identity.domain.model.User;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.models.infrastructure.persistence.repository.ModelRatingRepository;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.models.application.service.ModelRatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelRatingServiceImpl implements ModelRatingService {

    private final ModelRatingRepository modelRatingRepository;
    private final ScanRepository scanRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ModelRatingResponse createOrUpdateRating(UUID scanId, CreateModelRatingRequest request, UUID userId) {
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan session not found with ID: " + scanId));

        Optional<ModelRating> existingRatingOpt = (userId != null)
                ? modelRatingRepository.findByScanIdAndUserId(scanId, userId)
                : modelRatingRepository.findByScanIdAndUserIdIsNull(scanId);

        ModelRating savedRating;
        if (existingRatingOpt.isPresent()) {
            ModelRating rating = existingRatingOpt.get();
            rating.setRating(request.getRating());
            rating.setComment(request.getComment());
            savedRating = modelRatingRepository.save(rating);
            log.info("Updated existing model rating ID: {} for scan ID: {}, userId: {}, new rating: {}",
                    savedRating.getId(), scanId, userId, request.getRating());
        } else {
            User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;
            ModelRating newRating = ModelRating.builder()
                    .scan(scan)
                    .user(user)
                    .rating(request.getRating())
                    .comment(request.getComment())
                    .build();
            savedRating = modelRatingRepository.save(newRating);
            log.info("Created new model rating ID: {} for scan ID: {}, userId: {}, rating: {}",
                    savedRating.getId(), scanId, userId, request.getRating());
        }

        return mapToResponse(savedRating);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<ModelRatingResponse> getScanRatings(UUID scanId) {
        log.info("Fetching ratings for scan ID: {}", scanId);
        scanRepository.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan session not found with ID: " + scanId));

        return modelRatingRepository.findByScanIdOrderByCreatedAtDesc(scanId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteMyRating(UUID scanId, UUID userId) {
        log.info("Deleting rating for scan ID: {}, userId: {}", scanId, userId);
        scanRepository.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Scan session not found with ID: " + scanId));

        if (userId == null) {
            throw new IllegalArgumentException("User ID is required to delete rating");
        }

        modelRatingRepository.deleteByScanIdAndUserId(scanId, userId);
    }

    private ModelRatingResponse mapToResponse(ModelRating rating) {
        return ModelRatingResponse.builder()
                .id(rating.getId())
                .scanId(rating.getScan().getId())
                .userId(rating.getUser() != null ? rating.getUser().getId() : null)
                .rating(rating.getRating())
                .comment(rating.getComment())
                .createdAt(rating.getCreatedAt())
                .build();
    }
}
