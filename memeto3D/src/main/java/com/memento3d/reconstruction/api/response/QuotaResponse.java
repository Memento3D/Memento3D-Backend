package com.memento3d.reconstruction.api.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuotaResponse {
    private int totalQuota;
    private int usedQuota;
    private int remainingQuota;
}
