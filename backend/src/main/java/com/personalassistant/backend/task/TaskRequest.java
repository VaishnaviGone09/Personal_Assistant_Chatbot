package com.personalassistant.backend.task;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * JSON body used to create or update a task.
 * Example:
 * {"title":"Team meeting","description":"Weekly sync","date":"2026-10-20",
 *  "startTime":"10:00","endTime":"11:00","priority":"HIGH","status":"PENDING"}
 */
public record TaskRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 100, message = "Title must be at most 100 characters")
        String title,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @NotNull(message = "Date is required (format: yyyy-MM-dd)")
        LocalDate date,

        LocalTime startTime,

        LocalTime endTime,

        TaskPriority priority,

        TaskStatus status) {
}
