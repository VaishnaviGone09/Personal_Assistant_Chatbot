package com.personalassistant.backend.exception;

/** Thrown when a requested item (for example a task id) does not exist. Returns HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
