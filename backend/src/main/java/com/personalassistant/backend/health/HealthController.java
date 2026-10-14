package com.personalassistant.backend.health;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.personalassistant.backend.task.TaskRepository;

@RestController
public class HealthController {

    private final TaskRepository taskRepository;

    public HealthController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /** GET /api/health - returns 200 when the app and database are working, 503 otherwise. */
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean databaseUp;
        try {
            taskRepository.count();
            databaseUp = true;
        } catch (RuntimeException ex) {
            databaseUp = false;
        }
        body.put("status", databaseUp ? "UP" : "DOWN");
        body.put("database", databaseUp ? "UP" : "DOWN");
        body.put("application", "personal-assistant-backend");
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(databaseUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
