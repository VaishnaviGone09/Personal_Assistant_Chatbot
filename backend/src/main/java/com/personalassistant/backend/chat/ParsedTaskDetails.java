package com.personalassistant.backend.chat;

import java.time.LocalDate;
import java.time.LocalTime;

import com.personalassistant.backend.task.TaskPriority;

/**
 * Task details found in a chat message. Any field can be null if it was not mentioned.
 * {@code endTimeAssumed} is true when no end time or duration was given and a default length was used.
 */
public record ParsedTaskDetails(
        String title,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        TaskPriority priority,
        boolean endTimeAssumed) {
}
