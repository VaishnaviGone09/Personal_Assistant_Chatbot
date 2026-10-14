package com.personalassistant.backend.task;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** GET /api/tasks  (optional filters: ?date=2026-10-20&status=PENDING&priority=HIGH) */
    @GetMapping
    public List<TaskResponse> listTasks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority) {
        return taskService.getTasks(date, status, priority);
    }

    /** GET /api/tasks/today */
    @GetMapping("/today")
    public List<TaskResponse> todayTasks() {
        return taskService.getTodayTasks();
    }

    /** GET /api/tasks/{id} */
    @GetMapping("/{id}")
    public TaskResponse getTask(@PathVariable Long id) {
        return taskService.getTask(id);
    }

    /** POST /api/tasks  (add ?allowConflict=true to save even if it overlaps another task) */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskRequest request,
            @RequestParam(defaultValue = "false") boolean allowConflict) {
        TaskResponse created = taskService.createTask(request, allowConflict);
        return ResponseEntity.created(URI.create("/api/tasks/" + created.id())).body(created);
    }

    /** PUT /api/tasks/{id}  (replaces all fields of the task) */
    @PutMapping("/{id}")
    public TaskResponse updateTask(@PathVariable Long id, @Valid @RequestBody TaskRequest request,
            @RequestParam(defaultValue = "false") boolean allowConflict) {
        return taskService.updateTask(id, request, allowConflict);
    }

    /** PATCH /api/tasks/{id}/status  with body {"status":"COMPLETED"} */
    @PatchMapping("/{id}/status")
    public TaskResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return taskService.updateStatus(id, request.status());
    }

    /** DELETE /api/tasks/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
