package com.personalassistant.backend.schedule;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.personalassistant.backend.exception.BadRequestException;
import com.personalassistant.backend.task.Task;
import com.personalassistant.backend.task.TaskRepository;
import com.personalassistant.backend.task.TaskStatus;

/**
 * Detects overlapping tasks and finds free time.
 *
 * Rules:
 * - Only tasks with both a start and end time take up time. Cancelled tasks are ignored.
 * - Two tasks overlap when one starts before the other ends (10:00-11:00 and 11:00-12:00 do NOT overlap).
 * - Free time is searched inside the working day (app.schedule.day-start / day-end).
 * - For today, time that has already passed is never offered; past dates have no free time.
 */
@Service
public class ScheduleService {

    public static final int MAX_SUGGESTIONS = 3;

    private final TaskRepository taskRepository;
    private final Clock clock;
    private final LocalTime dayStart;
    private final LocalTime dayEnd;
    private final int slotStepMinutes;

    public ScheduleService(TaskRepository taskRepository,
            Clock clock,
            @Value("${app.schedule.day-start:08:00}") String dayStart,
            @Value("${app.schedule.day-end:20:00}") String dayEnd,
            @Value("${app.schedule.slot-step-minutes:30}") int slotStepMinutes) {
        this.taskRepository = taskRepository;
        this.clock = clock;
        this.dayStart = LocalTime.parse(dayStart);
        this.dayEnd = LocalTime.parse(dayEnd);
        this.slotStepMinutes = Math.max(5, slotStepMinutes);
    }

    public LocalTime getDayStart() {
        return dayStart;
    }

    public LocalTime getDayEnd() {
        return dayEnd;
    }

    /** Returns the tasks on {@code date} that overlap the given time range (excluding {@code excludeTaskId}). */
    public List<Task> findConflicts(LocalDate date, LocalTime start, LocalTime end, Long excludeTaskId) {
        return busyTasks(date, excludeTaskId).stream()
                .filter(task -> start.isBefore(task.getEndTime()) && task.getStartTime().isBefore(end))
                .toList();
    }

    /** Returns every free gap of at least {@code minDurationMinutes} on {@code date}. */
    public List<TimeSlot> findFreeSlots(LocalDate date, int minDurationMinutes, Long excludeTaskId) {
        validateDuration(minDurationMinutes);
        List<TimeSlot> freeSlots = new ArrayList<>();

        int windowStart = toMinutes(dayStart);
        int windowEnd = toMinutes(dayEnd);

        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today)) {
            return freeSlots;
        }
        if (date.equals(today)) {
            int now = toMinutes(LocalTime.now(clock));
            int nextQuarterHour = ((now + 14) / 15) * 15;
            windowStart = Math.max(windowStart, nextQuarterHour);
        }

        int cursor = windowStart;
        for (Task task : busyTasks(date, excludeTaskId)) {
            if (cursor >= windowEnd) {
                break;
            }
            int taskStart = toMinutes(task.getStartTime());
            int taskEnd = toMinutes(task.getEndTime());
            if (taskStart > cursor) {
                addIfLongEnough(freeSlots, cursor, Math.min(taskStart, windowEnd), minDurationMinutes);
            }
            cursor = Math.max(cursor, taskEnd);
        }
        if (cursor < windowEnd) {
            addIfLongEnough(freeSlots, cursor, windowEnd, minDurationMinutes);
        }
        return freeSlots;
    }

    /**
     * Suggests up to {@link #MAX_SUGGESTIONS} free slots of exactly {@code durationMinutes},
     * preferring the ones closest to {@code preferredStart}. Results are sorted by start time.
     */
    public List<TimeSlot> suggestSlots(LocalDate date, int durationMinutes, LocalTime preferredStart, Long excludeTaskId) {
        List<TimeSlot> candidates = new ArrayList<>();
        for (TimeSlot gap : findFreeSlots(date, durationMinutes, excludeTaskId)) {
            int gapEnd = toMinutes(gap.endTime());
            for (int start = toMinutes(gap.startTime()); start + durationMinutes <= gapEnd; start += slotStepMinutes) {
                candidates.add(TimeSlot.ofMinutes(start, start + durationMinutes));
            }
        }
        int preferred = preferredStart == null ? 0 : toMinutes(preferredStart);
        return candidates.stream()
                .sorted(Comparator.comparingInt((TimeSlot slot) -> Math.abs(toMinutes(slot.startTime()) - preferred))
                        .thenComparing(TimeSlot::startTime))
                .limit(MAX_SUGGESTIONS)
                .sorted(Comparator.comparing(TimeSlot::startTime))
                .toList();
    }

    /** Validates a start/end pair. Both must be given together, and end must be after start. */
    public static void validateTimeRange(LocalTime start, LocalTime end) {
        if (start == null && end == null) {
            return;
        }
        if (start == null || end == null) {
            throw new BadRequestException("Provide both startTime and endTime, or neither of them");
        }
        if (!end.isAfter(start)) {
            throw new BadRequestException("endTime must be after startTime");
        }
    }

    public static int minutesBetween(LocalTime start, LocalTime end) {
        return toMinutes(end) - toMinutes(start);
    }

    private List<Task> busyTasks(LocalDate date, Long excludeTaskId) {
        return taskRepository.findByDateOrderByStartTimeAsc(date).stream()
                .filter(Task::hasTimeRange)
                .filter(task -> task.getStatus() != TaskStatus.CANCELLED)
                .filter(task -> !Objects.equals(task.getId(), excludeTaskId))
                .sorted(Comparator.comparing(Task::getStartTime))
                .toList();
    }

    private void addIfLongEnough(List<TimeSlot> slots, int start, int end, int minDurationMinutes) {
        if (end - start >= minDurationMinutes) {
            slots.add(TimeSlot.ofMinutes(start, end));
        }
    }

    private static void validateDuration(int durationMinutes) {
        if (durationMinutes < 5 || durationMinutes > 720) {
            throw new BadRequestException("durationMinutes must be between 5 and 720");
        }
    }

    private static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }
}
