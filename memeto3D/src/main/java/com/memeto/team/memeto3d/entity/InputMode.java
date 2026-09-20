package com.memeto.team.memeto3d.entity;

public enum InputMode {
    image,
    video;

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (InputMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    public static InputMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return image;
        }
        for (InputMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Invalid input_mode: " + value + ". Must be 'image' or 'video'");
    }
}
