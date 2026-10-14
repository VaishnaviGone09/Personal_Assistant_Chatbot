package com.personalassistant.backend.chat;

import java.time.LocalDateTime;
import java.util.List;

import com.personalassistant.backend.schedule.TimeSlot;
import com.personalassistant.backend.task.TaskResponse;

/**
 * Reply from POST /api/chat.
 * "intent" tells the frontend what the assistant understood (e.g. LIST_TASKS, CREATE_TASK).
 * "source" is "rule-based" because this version uses the built-in Java fallback (no external AI).
 */
public record ChatResponse(
        String reply,
        String intent,
        String source,
        List<TaskResponse> tasks,
        TaskResponse createdTask,
        List<TimeSlot> suggestedSlots,
        LocalDateTime timestamp) {

    public static ChatResponse of(String reply, String intent, List<TaskResponse> tasks, TaskResponse createdTask,
            List<TimeSlot> suggestedSlots) {
        return new ChatResponse(reply, intent, "rule-based", tasks == null ? List.of() : tasks, createdTask,
                suggestedSlots == null ? List.of() : suggestedSlots, LocalDateTime.now());
    }

    public static ChatResponse text(String reply, String intent) {
        return of(reply, intent, null, null, null);
    }
}
