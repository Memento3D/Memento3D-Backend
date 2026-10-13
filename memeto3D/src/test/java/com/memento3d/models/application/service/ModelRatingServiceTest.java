package com.memento3d.models.application.service;

import com.memento3d.models.api.request.CreateModelRatingRequest;
import com.memento3d.models.api.response.ModelRatingResponse;
import com.memento3d.models.domain.model.ModelRating;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.identity.domain.model.User;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.models.infrastructure.persistence.repository.ModelRatingRepository;
import com.memento3d.scans.infrastructure.persistence.repository.ScanRepository;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.models.application.service.impl.ModelRatingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelRatingServiceTest {

    @Mock
    private ModelRatingRepository modelRatingRepository;

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ModelRatingServiceImpl modelRatingService;

    private UUID scanId;
    private UUID userId;
    private Scan mockScan;
    private User mockUser;

    @BeforeEach
    void setUp() {
        scanId = UUID.randomUUID();
        userId = UUID.randomUUID();

        mockUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .fullName("Test User")
                .build();

        mockScan = Scan.builder()
                .id(scanId)
                .user(mockUser)
                .name("Sample Scan")
                .build();
    }

    @Test
    @DisplayName("Create rating for first time -> Creates new record successfully")
    void testCreateRating_Success() {
        CreateModelRatingRequest request = CreateModelRatingRequest.builder()
                .rating(5)
                .comment("Excellent quality!")
                .build();

        when(scanRepository.findById(scanId)).thenReturn(Optional.of(mockScan));
        when(modelRatingRepository.findByScanIdAndUserId(scanId, userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        ModelRating savedEntity = ModelRating.builder()
                .id(UUID.randomUUID())
                .scan(mockScan)
                .user(mockUser)
                .rating(5)
                .comment("Excellent quality!")
                .createdAt(Instant.now())
                .build();

        when(modelRatingRepository.save(any(ModelRating.class))).thenReturn(savedEntity);

        ModelRatingResponse response = modelRatingService.createOrUpdateRating(scanId, request, userId);

        assertThat(response).isNotNull();
        assertThat(response.getScanId()).isEqualTo(scanId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Excellent quality!");

        verify(modelRatingRepository, times(1)).save(any(ModelRating.class));
    }

    @Test
    @DisplayName("Update existing rating -> Updates existing record without duplicate")
    void testUpdateRating_Success() {
        CreateModelRatingRequest updateRequest = CreateModelRatingRequest.builder()
                .rating(4)
                .comment("Updated comment")
                .build();

        ModelRating existingRating = ModelRating.builder()
                .id(UUID.randomUUID())
                .scan(mockScan)
                .user(mockUser)
                .rating(2)
                .comment("Initial comment")
                .createdAt(Instant.now())
                .build();

        when(scanRepository.findById(scanId)).thenReturn(Optional.of(mockScan));
        when(modelRatingRepository.findByScanIdAndUserId(scanId, userId)).thenReturn(Optional.of(existingRating));
        when(modelRatingRepository.save(any(ModelRating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModelRatingResponse response = modelRatingService.createOrUpdateRating(scanId, updateRequest, userId);

        assertThat(response).isNotNull();
        assertThat(response.getRating()).isEqualTo(4);
        assertThat(response.getComment()).isEqualTo("Updated comment");

        verify(modelRatingRepository, times(1)).save(existingRating);
    }

    @Test
    @DisplayName("Rating < 1 -> Throws IllegalArgumentException")
    void testRating_TooLow_ThrowsIllegalArgumentException() {
        CreateModelRatingRequest request = CreateModelRatingRequest.builder()
                .rating(0)
                .build();

        assertThatThrownBy(() -> modelRatingService.createOrUpdateRating(scanId, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rating must be between 1 and 5");
    }

    @Test
    @DisplayName("Rating > 5 -> Throws IllegalArgumentException")
    void testRating_TooHigh_ThrowsIllegalArgumentException() {
        CreateModelRatingRequest request = CreateModelRatingRequest.builder()
                .rating(6)
                .build();

        assertThatThrownBy(() -> modelRatingService.createOrUpdateRating(scanId, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rating must be between 1 and 5");
    }

    @Test
    @DisplayName("Scan non-existent -> Throws ResourceNotFoundException")
    void testRating_ScanNotFound_ThrowsResourceNotFoundException() {
        CreateModelRatingRequest request = CreateModelRatingRequest.builder()
                .rating(5)
                .build();

        when(scanRepository.findById(scanId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modelRatingService.createOrUpdateRating(scanId, request, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Scan session not found");
    }

    @Test
    @DisplayName("Guest rating (userId = null) -> Creates guest rating record successfully")
    void testGuestRating_Success() {
        CreateModelRatingRequest request = CreateModelRatingRequest.builder()
                .rating(5)
                .comment("Guest rating")
                .build();

        when(scanRepository.findById(scanId)).thenReturn(Optional.of(mockScan));
        when(modelRatingRepository.findByScanIdAndUserIdIsNull(scanId)).thenReturn(Optional.empty());

        ModelRating savedEntity = ModelRating.builder()
                .id(UUID.randomUUID())
                .scan(mockScan)
                .user(null)
                .rating(5)
                .comment("Guest rating")
                .createdAt(Instant.now())
                .build();

        when(modelRatingRepository.save(any(ModelRating.class))).thenReturn(savedEntity);

        ModelRatingResponse response = modelRatingService.createOrUpdateRating(scanId, request, null);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isNull();
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Guest rating");
    }
}
