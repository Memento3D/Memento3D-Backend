package com.memeto.team.memeto3d.service.impl;

import com.memeto.team.memeto3d.dto.CreateModelRatingRequest;
import com.memeto.team.memeto3d.dto.ModelRatingResponse;
import com.memeto.team.memeto3d.entity.ModelRating;
import com.memeto.team.memeto3d.entity.Scan;
import com.memeto.team.memeto3d.entity.User;
import com.memeto.team.memeto3d.exception.ResourceNotFoundException;
import com.memeto.team.memeto3d.repository.ModelRatingRepository;
import com.memeto.team.memeto3d.repository.ScanRepository;
import com.memeto.team.memeto3d.repository.UserRepository;
import com.memeto.team.memeto3d.service.ModelRatingService;
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
