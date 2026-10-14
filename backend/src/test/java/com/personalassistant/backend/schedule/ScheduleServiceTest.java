package com.personalassistant.backend.schedule;

import static com.personalassistant.backend.TestClockConfig.TODAY;
import static com.personalassistant.backend.TestClockConfig.TOMORROW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.personalassistant.backend.TestClockConfig;
import com.personalassistant.backend.exception.BadRequestException;
import com.personalassistant.backend.task.Task;
import com.personalassistant.backend.task.TaskRepository;
import com.personalassistant.backend.task.TaskStatus;

@DataJpaTest
class ScheduleServiceTest {

    @Autowired
    private TaskRepository taskRepository;

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        // Working day 08:00-20:00, "now" is 2026-10-14 09:00
        scheduleService = new ScheduleService(taskRepository, TestClockConfig.fixedClock(), "08:00", "20:00", 30);
    }

    @Test
    void emptyFutureDayIsCompletelyFree() {
        assertThat(scheduleService.findFreeSlots(TOMORROW, 30, null))
                .containsExactly(slot("08:00", "20:00"));
    }

    @Test
    void freeSlotsSkipBusyTimeAndMergeOverlappingTasks() {
        save(TOMORROW, "09:00", "10:00", TaskStatus.PENDING);
        save(TOMORROW, "09:30", "11:00", TaskStatus.PENDING); // overlaps the first one
        save(TOMORROW, "13:00", "14:00", TaskStatus.COMPLETED);

        assertThat(scheduleService.findFreeSlots(TOMORROW, 30, null))
                .containsExactly(slot("08:00", "09:00"), slot("11:00", "13:00"), slot("14:00", "20:00"));
    }

    @Test
    void cancelledAndUntimedTasksDoNotBlockTime() {
        save(TOMORROW, "09:00", "10:00", TaskStatus.CANCELLED);
        Task allDay = new Task();
        allDay.setTitle("All day");
        allDay.setDate(TOMORROW);
        taskRepository.save(allDay);

        assertThat(scheduleService.findFreeSlots(TOMORROW, 30, null)).containsExactly(slot("08:00", "20:00"));
        assertThat(scheduleService.findConflicts(TOMORROW, LocalTime.of(9, 0), LocalTime.of(10, 0), null)).isEmpty();
    }

    @Test
    void gapsShorterThanRequestedDurationAreIgnored() {
        save(TOMORROW, "08:00", "09:00", TaskStatus.PENDING);
        save(TOMORROW, "09:20", "19:00", TaskStatus.PENDING);

        assertThat(scheduleService.findFreeSlots(TOMORROW, 30, null)).containsExactly(slot("19:00", "20:00"));
        assertThat(scheduleService.findFreeSlots(TOMORROW, 15, null))
                .containsExactly(slot("09:00", "09:20"), slot("19:00", "20:00"));
    }

    @Test
    void todayNeverOffersTimeThatHasAlreadyPassed() {
        // now is 09:00, so nothing before 09:00 may be suggested
        assertThat(scheduleService.findFreeSlots(TODAY, 30, null)).containsExactly(slot("09:00", "20:00"));
    }

    @Test
    void pastDaysHaveNoFreeSlots() {
        assertThat(scheduleService.findFreeSlots(TODAY.minusDays(1), 30, null)).isEmpty();
    }

    @Test
    void touchingTasksDoNotConflictButOverlappingOnesDo() {
        Task existing = save(TOMORROW, "10:00", "11:00", TaskStatus.PENDING);

        assertThat(scheduleService.findConflicts(TOMORROW, LocalTime.of(11, 0), LocalTime.of(12, 0), null)).isEmpty();
        assertThat(scheduleService.findConflicts(TOMORROW, LocalTime.of(9, 0), LocalTime.of(10, 0), null)).isEmpty();
        assertThat(scheduleService.findConflicts(TOMORROW, LocalTime.of(10, 30), LocalTime.of(11, 30), null))
                .extracting(Task::getId).containsExactly(existing.getId());
        // A task never conflicts with itself (used when updating)
        assertThat(scheduleService.findConflicts(TOMORROW, LocalTime.of(10, 30), LocalTime.of(11, 30), existing.getId()))
                .isEmpty();
    }

    @Test
    void suggestionsAreFreeHaveTheRightLengthAndAreNearThePreferredTime() {
        save(TOMORROW, "08:00", "12:00", TaskStatus.PENDING);
        save(TOMORROW, "13:00", "18:00", TaskStatus.PENDING);

        List<TimeSlot> suggestions = scheduleService.suggestSlots(TOMORROW, 60, LocalTime.of(10, 0), null);

        assertThat(suggestions).containsExactly(slot("12:00", "13:00"), slot("18:00", "19:00"), slot("18:30", "19:30"));
        for (TimeSlot suggestion : suggestions) {
            assertThat(suggestion.durationMinutes()).isEqualTo(60);
            assertThat(scheduleService.findConflicts(TOMORROW, suggestion.startTime(), suggestion.endTime(), null))
                    .isEmpty();
        }
    }

    @Test
    void noSuggestionsWhenTheDayIsFull() {
        save(TOMORROW, "08:00", "20:00", TaskStatus.PENDING);
        assertThat(scheduleService.suggestSlots(TOMORROW, 30, LocalTime.of(10, 0), null)).isEmpty();
    }

    @Test
    void invalidTimeRangesAreRejected() {
        assertThatThrownBy(() -> ScheduleService.validateTimeRange(LocalTime.of(10, 0), null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ScheduleService.validateTimeRange(LocalTime.of(10, 0), LocalTime.of(10, 0)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> scheduleService.findFreeSlots(TOMORROW, 0, null))
                .isInstanceOf(BadRequestException.class);
    }

    private Task save(LocalDate date, String start, String end, TaskStatus status) {
        Task task = new Task();
        task.setTitle("Task " + start);
        task.setDate(date);
        task.setStartTime(LocalTime.parse(start));
        task.setEndTime(LocalTime.parse(end));
        task.setStatus(status);
        return taskRepository.save(task);
    }

    private static TimeSlot slot(String start, String end) {
        LocalTime s = LocalTime.parse(start);
        LocalTime e = LocalTime.parse(end);
        return new TimeSlot(s, e, ScheduleService.minutesBetween(s, e));
    }
}
