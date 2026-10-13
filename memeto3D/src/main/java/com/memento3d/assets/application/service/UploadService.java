package com.memento3d.assets.application.service;

import com.memento3d.identity.domain.model.User;
import com.memento3d.scans.domain.model.Scan;

import com.memento3d.assets.api.response.UploadStatusResponse;
import com.memento3d.identity.domain.model.Role;

import java.util.UUID;

public interface UploadService {

    /**
     * Returns the upload status for a chunked upload session identified by
     * {@code scanId} (the {@code id} path variable in the REST endpoint).
     *
     * <p>The "session" here is the combination of a scan and its most-recent
     * (or active) {@code file_reference}.  When multiple files are uploaded to
     * the same scan, callers should supply the {@code fileReference} query
     * parameter to disambiguate; when omitted the service uses the latest one.</p>
     *
     * @param scanId          scan UUID from the URL path
     * @param fileReference   optional; the filename / session key — null means
     *                        "return status for the most-recent active session"
     * @param currentUserId   authenticated user
     * @param currentUserRole authenticated role (admin/operator bypass IDOR)
     * @return upload status including chunk counts and missing chunk indexes
     * @throws com.memento3d.shared.exception.ResourceNotFoundException
     *         when the scan or upload session does not exist, or IDOR check fails
     */
    UploadStatusResponse getUploadStatus(UUID scanId,
                                         String fileReference,
                                         UUID currentUserId,
                                         Role currentUserRole);
}
