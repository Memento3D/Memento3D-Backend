package com.memeto.team.memeto3d.service;

import com.memeto.team.memeto3d.dto.CreateModelRatingRequest;
import com.memeto.team.memeto3d.dto.ModelRatingResponse;

import java.util.UUID;

public interface ModelRatingService {

    ModelRatingResponse createOrUpdateRating(UUID scanId, CreateModelRatingRequest request, UUID userId);
}
