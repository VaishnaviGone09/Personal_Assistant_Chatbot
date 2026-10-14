package com.personalassistant.backend.task;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalassistant.backend.exception.ResourceNotFoundException;
import com.personalassistant.backend.exception.ScheduleConflictException;
import com.personalassistant.backend.schedule.ScheduleService;
import com.personalassistant.backend.schedule.TimeSlot;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ScheduleService scheduleService;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, ScheduleService scheduleService, Clock clock) {
        this.taskRepository = taskRepository;
        this.scheduleService = scheduleService;
        this.clock = clock;
    }

    /** Lists tasks, optionally filtered by date, status and/or priority. */
    @Transactional(readOnly = true)
    public List<TaskResponse> getTasks(LocalDate date, TaskStatus status, TaskPriority priority) {
        List<Task> tasks = date != null
                ? taskRepository.findByDateOrderByStartTimeAsc(date)
                : taskRepository.findAllByOrderByDateAscStartTimeAsc();
        return tasks.stream()
                .filter(task -> status == null || task.getStatus() == status)
                .filter(task -> priority == null || task.getPriority() == priority)
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksForDate(LocalDate date) {
        return getTasks(date, null, null);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTodayTasks() {
        return getTasksForDate(LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long id) {
        return TaskResponse.from(findTask(id));
    }

    /**
     * Creates a task. If it overlaps another task, a ScheduleConflictException (HTTP 409) is thrown
     * unless {@code allowConflict} is true.
     */
    @Transactional
    public TaskResponse createTask(TaskRequest request, boolean allowConflict) {
        Task task = new Task();
        applyRequest(task, request);
        checkForConflicts(task, allowConflict);
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateTask(Long id, TaskRequest request, boolean allowConflict) {
        Task task = findTask(id);
        applyRequest(task, request);
        checkForConflicts(task, allowConflict);
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateStatus(Long id, TaskStatus newStatus) {
        Task task = findTask(id);
        boolean reactivating = task.getStatus() == TaskStatus.CANCELLED && newStatus != TaskStatus.CANCELLED;
        task.setStatus(newStatus);
        if (reactivating) {
            // A cancelled task did not block time, so make sure it still fits before bringing it back.
            checkForConflicts(task, false);
        }
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(Long id) {
        taskRepository.delete(findTask(id));
    }

    private Task findTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task with id " + id + " was not found"));
    }

    private void applyRequest(Task task, TaskRequest request) {
        ScheduleService.validateTimeRange(request.startTime(), request.endTime());
        task.setTitle(request.title().trim());
        task.setDescription(request.description() == null ? null : request.description().trim());
        task.setDate(request.date());
        task.setStartTime(request.startTime());
        task.setEndTime(request.endTime());
        task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
        task.setStatus(request.status() == null ? TaskStatus.PENDING : request.status());
    }

    private void checkForConflicts(Task task, boolean allowConflict) {
        if (allowConflict || !task.hasTimeRange() || task.getStatus() == TaskStatus.CANCELLED) {
            return;
        }
        List<TaskResponse> conflicts = scheduleService
                .findConflicts(task.getDate(), task.getStartTime(), task.getEndTime(), task.getId()).stream()
                .map(TaskResponse::from)
                .toList();
        if (conflicts.isEmpty()) {
            return;
        }
        int duration = ScheduleService.minutesBetween(task.getStartTime(), task.getEndTime());
        List<TimeSlot> suggestions = scheduleService.suggestSlots(task.getDate(), duration, task.getStartTime(),
                task.getId());
        throw new ScheduleConflictException(
                "This time overlaps " + conflicts.size() + " existing task(s). "
                        + "Pick a suggested slot or send allowConflict=true to save anyway.",
                conflicts, suggestions);
    }
}
