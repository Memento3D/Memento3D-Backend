package com.memento3d.reconstruction.api.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ReconstructionCapabilitiesResponse {
    private String provider;
    private int minImages;
    private int maxImages;
    private List<String> supportedFormats;
    private int maxFileSizeMb;
    private String guidelines;
}
