package com.andreaitemmaker.config;

/** Thrown when a content config file is invalid. The message identifies the problem precisely. */
public class ConfigException extends RuntimeException {

    private final String file;

    public ConfigException(String file, String message) {
        super(message);
        this.file = file;
    }

    public ConfigException(String file, String message, Throwable cause) {
        super(message, cause);
        this.file = file;
    }

    public String getFile() {
        return file;
    }
}
