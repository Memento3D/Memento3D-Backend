package com.memento3d.models.application.service;

import com.memento3d.models.api.request.CreateModelRatingRequest;
import com.memento3d.models.api.response.ModelRatingResponse;

import java.util.UUID;

public interface ModelRatingService {

    ModelRatingResponse createOrUpdateRating(UUID scanId, CreateModelRatingRequest request, UUID userId);
}
