# Image Deletion Feature Implementation (UC-05)

## Overview
Implemented DELETE endpoint to remove individual images from scans before or after quality check.

## Endpoint
- **DELETE** `/api/v1/scans/{id}/images/{image_id}`
- **Returns**: 204 No Content on success

## Feature Details

### Endpoint Behavior

#### Success Cases (204 No Content)
- Image successfully deleted from database
- Physical file storage path no longer needed (cleanup handled by storage service)
- Scan counters updated: `file_count -= 1`, `total_size_bytes -= image_size`
- User storage quota released: `storage_used_bytes -= image_size`
- Related `image_quality_results` automatically deleted via ON DELETE CASCADE

#### Error Cases

| Status | Condition | Message |
|--------|-----------|---------|
| 401 | Unauthorized (missing/invalid JWT) | "Unauthorized" |
| 404 | Scan not found | "Scan not found with ID: {id}" |
| 404 | Image not found | "Image not found with ID: {image_id}" |
| 404 | Image doesn't belong to scan | "Image not found with ID: {image_id}" |
| 404 | IDOR: Non-owner deleting other's image | "Scan not found with ID: {id}" |
| 409 | Scan in 'processing' status | "Cannot delete images while the scan is in 'processing' status" |

### Access Control
- **Owner**: Can delete their own images
- **Admin/Operator**: Can delete any image
- Non-owners get 404 (IDOR protection)

### Business Logic
1. Verify scan exists and not deleted
2. IDOR check (ownership validation)
3. Block deletion if scan status == 'processing'
4. Fetch and validate image belongs to scan
5. Hard-delete (not soft-delete) - images don't need long-term retention
6. Cascade deletes image_quality_results via foreign key constraint
7. Update scan: file_count, total_size_bytes, updated_at
8. Update user: storage_used_bytes (release quota)

### Implementation Files

#### 1. Service Interface
**File**: `src/main/java/com/memeto/team/memeto3d/service/ScanService.java`
- Added method: `void deleteImageFromScan(UUID scanId, UUID imageId, UUID currentUserId, Role currentUserRole)`
- Documented exceptions and behavior in JavaDoc

#### 2. Service Implementation
**File**: `src/main/java/com/memeto/team/memeto3d/service/impl/ScanServiceImpl.java`
- Implemented complete deletion logic
- Transactional with proper rollback on error
- Validates all preconditions before deletion
- Updates multiple entities atomically

#### 3. Controller
**File**: `src/main/java/com/memeto/team/memeto3d/controller/ScanController.java`
- Added DELETE endpoint: `/{id}/images/{image_id}`
- Proper OpenAPI documentation with all response codes
- Security annotation for authentication requirement

### Database Behavior
- **Hard delete**: Image record removed entirely (not soft-deleted with deleted_at)
- **Cascade**: `image_quality_results` automatically deleted by DB foreign key
- **Atomic**: Single transaction for image, scan, and user updates
- **No physical file deletion**: Storage service responsible for cleanup

### Testing Checklist
- [ ] Delete valid image → 204
- [ ] Delete non-existent image → 404
- [ ] Delete image not in scan → 404
- [ ] Delete as non-owner → 404
- [ ] Delete when scan processing → 409
- [ ] Verify file_count decremented
- [ ] Verify total_size_bytes decremented
- [ ] Verify storage_used_bytes released
- [ ] Verify image_quality_results cascade deleted
- [ ] Admin can delete any image
- [ ] Operator can delete any image
