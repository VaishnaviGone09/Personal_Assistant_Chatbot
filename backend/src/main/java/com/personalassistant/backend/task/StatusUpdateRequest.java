package com.personalassistant.backend.task;

import jakarta.validation.constraints.NotNull;

/** JSON body for PATCH /api/tasks/{id}/status, e.g. {"status":"COMPLETED"} */
public record StatusUpdateRequest(
        @NotNull(message = "Status is required (PENDING, IN_PROGRESS, COMPLETED or CANCELLED)")
        TaskStatus status) {
}
