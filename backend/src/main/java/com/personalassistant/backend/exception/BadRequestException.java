package com.personalassistant.backend.exception;

/** Thrown when the client sends invalid data that passes basic validation. Returns HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
