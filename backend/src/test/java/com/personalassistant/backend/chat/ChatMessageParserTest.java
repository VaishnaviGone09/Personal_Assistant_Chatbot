package com.personalassistant.backend.chat;

import static com.personalassistant.backend.TestClockConfig.TODAY;
import static com.personalassistant.backend.TestClockConfig.TOMORROW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.personalassistant.backend.TestClockConfig;
import com.personalassistant.backend.chat.ChatMessageParser.Intent;
import com.personalassistant.backend.task.TaskPriority;

class ChatMessageParserTest {

    // Today is Wednesday 2026-10-14
    private final ChatMessageParser parser = new ChatMessageParser(TestClockConfig.fixedClock());

    @Test
    void detectsIntents() {
        assertThat(parser.detectIntent("What are my tasks today?")).isEqualTo(Intent.LIST_TASKS);
        assertThat(parser.detectIntent("show my schedule for tomorrow")).isEqualTo(Intent.LIST_TASKS);
        assertThat(parser.detectIntent("List all tasks")).isEqualTo(Intent.LIST_TASKS);
        assertThat(parser.detectIntent("Add dentist tomorrow at 3pm")).isEqualTo(Intent.CREATE_TASK);
        assertThat(parser.detectIntent("Remind me to call mom at 6pm today")).isEqualTo(Intent.CREATE_TASK);
        assertThat(parser.detectIntent("Schedule a meeting with Sam")).isEqualTo(Intent.CREATE_TASK);
        assertThat(parser.detectIntent("When am I free tomorrow?")).isEqualTo(Intent.FREE_SLOTS);
        assertThat(parser.detectIntent("Give me my briefing")).isEqualTo(Intent.BRIEFING);
        assertThat(parser.detectIntent("hello")).isEqualTo(Intent.HELP);
        assertThat(parser.detectIntent("blah blah")).isEqualTo(Intent.UNKNOWN);
    }

    @Test
    void parsesFullTaskWithTimeRangeAndPriority() {
        ParsedTaskDetails details = parser.parseTaskDetails("Add Team meeting tomorrow from 10:00 to 11:30 high priority");

        assertThat(details.title()).isEqualTo("Team meeting");
        assertThat(details.date()).isEqualTo(TOMORROW);
        assertThat(details.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(details.endTime()).isEqualTo(LocalTime.of(11, 30));
        assertThat(details.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(details.endTimeAssumed()).isFalse();
    }

    @Test
    void parsesAmPmRangesAndIsoDates() {
        ParsedTaskDetails details = parser.parseTaskDetails("create task Gym session on 2026-10-20 3-4pm");
        assertThat(details.title()).isEqualTo("Gym session");
        assertThat(details.date()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(details.startTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(details.endTime()).isEqualTo(LocalTime.of(16, 0));

        ParsedTaskDetails crossNoon = parser.parseTaskDetails("add Workshop today 11-1pm");
        assertThat(crossNoon.startTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(crossNoon.endTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void usesDurationOrDefaultOneHourWhenOnlyStartTimeGiven() {
        ParsedTaskDetails withDuration = parser.parseTaskDetails("Remind me to call mom today at 6pm for 30 minutes");
        assertThat(withDuration.title()).isEqualTo("Call mom");
        assertThat(withDuration.date()).isEqualTo(TODAY);
        assertThat(withDuration.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(withDuration.endTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(withDuration.endTimeAssumed()).isFalse();

        ParsedTaskDetails withoutDuration = parser.parseTaskDetails("add Dentist on friday at 9:15am");
        assertThat(withoutDuration.title()).isEqualTo("Dentist");
        assertThat(withoutDuration.date()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(withoutDuration.startTime()).isEqualTo(LocalTime.of(9, 15));
        assertThat(withoutDuration.endTime()).isEqualTo(LocalTime.of(10, 15));
        assertThat(withoutDuration.endTimeAssumed()).isTrue();
    }

    @Test
    void quotedTitleIsUsedAsIs() {
        ParsedTaskDetails details = parser.parseTaskDetails("add \"Read chapter 5\" tomorrow at 20:00 low priority");
        assertThat(details.title()).isEqualTo("Read chapter 5");
        assertThat(details.startTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(details.priority()).isEqualTo(TaskPriority.LOW);
    }

    @Test
    void numbersThatAreNotTimesAreLeftInTheTitle() {
        ParsedTaskDetails details = parser.parseTaskDetails("add Buy 3 apples tomorrow at 17:00");
        assertThat(details.title()).isEqualTo("Buy 3 apples");
        assertThat(details.startTime()).isEqualTo(LocalTime.of(17, 0));
    }

    @Test
    void missingDetailsAreReportedAsNull() {
        ParsedTaskDetails details = parser.parseTaskDetails("add a task");
        assertThat(details.title()).isNull();
        assertThat(details.date()).isNull();
        assertThat(details.startTime()).isNull();
        assertThat(details.endTime()).isNull();
    }

    @Test
    void parsesDatesAndDurationsForOtherIntents() {
        assertThat(parser.parseDate("tasks today")).isEqualTo(TODAY);
        assertThat(parser.parseDate("tasks day after tomorrow")).isEqualTo(TODAY.plusDays(2));
        assertThat(parser.parseDate("what about next wednesday")).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(parser.parseDate("what about wednesday")).isEqualTo(TODAY);
        assertThat(parser.parseDate("show everything")).isNull();
        assertThat(parser.parseDurationMinutes("free for 2 hours")).isEqualTo(120);
        assertThat(parser.parseDurationMinutes("free for 45 min")).isEqualTo(45);
    }
}
