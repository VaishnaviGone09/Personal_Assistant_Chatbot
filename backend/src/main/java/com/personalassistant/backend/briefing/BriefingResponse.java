package com.personalassistant.backend.briefing;

import java.time.LocalDate;
import java.util.List;

import com.personalassistant.backend.schedule.TimeSlot;
import com.personalassistant.backend.task.TaskResponse;

/** Result of GET /api/briefing. */
public record BriefingResponse(
        LocalDate date,
        String greeting,
        String summary,
        int totalTasks,
        int completedTasks,
        int openTasks,
        int highPriorityOpenTasks,
        TaskResponse nextTask,
        List<TaskResponse> tasks,
        List<TimeSlot> freeSlots) {
}
