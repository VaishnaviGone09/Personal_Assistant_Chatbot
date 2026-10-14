package com.personalassistant.backend.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.personalassistant.backend.task.TaskResponse;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** Example: GET /api/schedule/free-slots?date=2026-10-20&durationMinutes=60 */
    @GetMapping("/free-slots")
    public FreeSlotsResponse freeSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "30") int durationMinutes) {
        List<TimeSlot> slots = scheduleService.findFreeSlots(date, durationMinutes, null);
        return new FreeSlotsResponse(date, scheduleService.getDayStart(), scheduleService.getDayEnd(), durationMinutes,
                slots);
    }

    /** Example: GET /api/schedule/check?date=2026-10-20&startTime=10:00&endTime=11:00 */
    @GetMapping("/check")
    public ConflictCheckResponse check(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(required = false) Long excludeTaskId) {
        ScheduleService.validateTimeRange(startTime, endTime);
        List<TaskResponse> conflicts = scheduleService.findConflicts(date, startTime, endTime, excludeTaskId).stream()
                .map(TaskResponse::from)
                .toList();
        List<TimeSlot> suggestions = conflicts.isEmpty()
                ? List.of()
                : scheduleService.suggestSlots(date, ScheduleService.minutesBetween(startTime, endTime), startTime,
                        excludeTaskId);
        return new ConflictCheckResponse(date, startTime, endTime, !conflicts.isEmpty(), conflicts, suggestions);
    }
}
