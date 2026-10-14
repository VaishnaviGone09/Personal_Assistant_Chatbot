package com.personalassistant.backend.briefing;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.personalassistant.backend.schedule.ScheduleService;
import com.personalassistant.backend.schedule.TimeSlot;
import com.personalassistant.backend.task.TaskPriority;
import com.personalassistant.backend.task.TaskResponse;
import com.personalassistant.backend.task.TaskService;
import com.personalassistant.backend.task.TaskStatus;

/** Builds a short daily summary of tasks and free time. */
@Service
public class BriefingService {

    private static final int FREE_SLOT_MINIMUM_MINUTES = 30;

    private final TaskService taskService;
    private final ScheduleService scheduleService;
    private final Clock clock;

    public BriefingService(TaskService taskService, ScheduleService scheduleService, Clock clock) {
        this.taskService = taskService;
        this.scheduleService = scheduleService;
        this.clock = clock;
    }

    public BriefingResponse getBriefing(LocalDate requestedDate) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = requestedDate == null ? today : requestedDate;
        boolean isToday = date.equals(today);

        List<TaskResponse> tasks = taskService.getTasksForDate(date).stream()
                .filter(task -> task.status() != TaskStatus.CANCELLED)
                .toList();
        List<TaskResponse> openTasks = tasks.stream().filter(BriefingService::isOpen).toList();
        int completed = tasks.size() - openTasks.size();
        int highPriorityOpen = (int) openTasks.stream().filter(t -> t.priority() == TaskPriority.HIGH).count();

        TaskResponse nextTask = findNextTask(openTasks, isToday).orElse(null);
        List<TimeSlot> freeSlots = scheduleService.findFreeSlots(date, FREE_SLOT_MINIMUM_MINUTES, null);

        String greeting = isToday ? greetingFor(LocalTime.now(clock)) : "Here is your plan for " + date + ".";
        String summary = buildSummary(isToday ? "today" : "on " + date, tasks.size(), completed, openTasks.size(),
                highPriorityOpen, nextTask, freeSlots);

        return new BriefingResponse(date, greeting, summary, tasks.size(), completed, openTasks.size(),
                highPriorityOpen, nextTask, tasks, freeSlots);
    }

    private Optional<TaskResponse> findNextTask(List<TaskResponse> openTasks, boolean isToday) {
        LocalTime now = LocalTime.now(clock);
        return openTasks.stream()
                .filter(task -> task.startTime() != null)
                // For today, skip tasks that have already finished.
                .filter(task -> !isToday || task.endTime() == null || task.endTime().isAfter(now))
                .findFirst();
    }

    private static boolean isOpen(TaskResponse task) {
        return task.status() == TaskStatus.PENDING || task.status() == TaskStatus.IN_PROGRESS;
    }

    private static String greetingFor(LocalTime time) {
        if (time.isBefore(LocalTime.NOON)) {
            return "Good morning!";
        }
        if (time.isBefore(LocalTime.of(17, 0))) {
            return "Good afternoon!";
        }
        return "Good evening!";
    }

    private static String buildSummary(String when, int total, int completed, int open, int highPriorityOpen,
            TaskResponse nextTask, List<TimeSlot> freeSlots) {
        if (total == 0) {
            return "You have no tasks " + when + "." + describeFreeTime(freeSlots);
        }
        StringBuilder text = new StringBuilder();
        text.append("You have ").append(total).append(" task(s) ").append(when)
                .append(": ").append(completed).append(" completed and ").append(open).append(" still open.");
        if (highPriorityOpen > 0) {
            text.append(" ").append(highPriorityOpen).append(" open task(s) are high priority.");
        }
        if (nextTask != null) {
            text.append(" Next up: '").append(nextTask.title()).append("' at ").append(nextTask.startTime()).append(".");
        }
        text.append(describeFreeTime(freeSlots));
        return text.toString();
    }

    private static String describeFreeTime(List<TimeSlot> freeSlots) {
        if (freeSlots.isEmpty()) {
            return " There is no free time left in your working hours.";
        }
        return " Free time: " + String.join(", ", freeSlots.stream().map(TimeSlot::toString).toList()) + ".";
    }
}
