package com.personalassistant.backend.schedule;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/** A block of time on a day, e.g. 14:00 - 15:00 (60 minutes). */
public record TimeSlot(
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        int durationMinutes) {

    public static TimeSlot ofMinutes(int startMinuteOfDay, int endMinuteOfDay) {
        return new TimeSlot(toTime(startMinuteOfDay), toTime(endMinuteOfDay), endMinuteOfDay - startMinuteOfDay);
    }

    private static LocalTime toTime(int minuteOfDay) {
        return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);
    }

    @Override
    public String toString() {
        return startTime + "-" + endTime;
    }
}
