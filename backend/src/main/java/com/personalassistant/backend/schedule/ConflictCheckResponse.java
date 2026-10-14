package com.personalassistant.backend.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.personalassistant.backend.task.TaskResponse;

/** Result of GET /api/schedule/check. */
public record ConflictCheckResponse(
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        boolean hasConflict,
        List<TaskResponse> conflicts,
        List<TimeSlot> suggestedSlots) {
}
