package com.memento3d.reconstruction.domain.exception;

import lombok.Getter;

@Getter
public class AiClientException extends RuntimeException {

    // null: lỗi kết nối, timeout hoặc response không hợp lệ.
    private final Integer upstreamStatus;

    public AiClientException(
            String message,
            Integer upstreamStatus
    ) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    public AiClientException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.upstreamStatus = null;
    }
}