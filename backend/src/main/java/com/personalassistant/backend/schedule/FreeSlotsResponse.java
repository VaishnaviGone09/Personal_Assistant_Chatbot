package com.personalassistant.backend.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Result of GET /api/schedule/free-slots. */
public record FreeSlotsResponse(
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime workdayStart,
        @JsonFormat(pattern = "HH:mm") LocalTime workdayEnd,
        int minimumDurationMinutes,
        List<TimeSlot> freeSlots) {
}
