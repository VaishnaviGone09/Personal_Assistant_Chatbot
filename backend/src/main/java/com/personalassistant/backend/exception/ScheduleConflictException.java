package com.personalassistant.backend.exception;

import java.util.List;

import com.personalassistant.backend.schedule.TimeSlot;
import com.personalassistant.backend.task.TaskResponse;

/** Thrown when a task overlaps existing tasks. Returns HTTP 409 with conflicts and free slot suggestions. */
public class ScheduleConflictException extends RuntimeException {

    private final List<TaskResponse> conflicts;
    private final List<TimeSlot> suggestedSlots;

    public ScheduleConflictException(String message, List<TaskResponse> conflicts, List<TimeSlot> suggestedSlots) {
        super(message);
        this.conflicts = conflicts;
        this.suggestedSlots = suggestedSlots;
    }

    public List<TaskResponse> getConflicts() {
        return conflicts;
    }

    public List<TimeSlot> getSuggestedSlots() {
        return suggestedSlots;
    }
}
