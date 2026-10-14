package com.personalassistant.backend.chat;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.personalassistant.backend.task.TaskPriority;

/**
 * A simple rule-based (regular expression) parser for chat messages.
 * It does not use any external AI service.
 *
 * Understood examples:
 * - "What are my tasks today?"  /  "Show tasks for tomorrow"  /  "List all tasks"
 * - "Add Team meeting tomorrow from 10:00 to 11:00 high priority"
 * - "Remind me to call mom on 2026-10-20 at 6pm for 30 minutes"
 * - "When am I free tomorrow?"  /  "Give me my briefing"
 */
@Component
public class ChatMessageParser {

    public enum Intent { CREATE_TASK, LIST_TASKS, FREE_SLOTS, BRIEFING, HELP, UNKNOWN }

    public static final int DEFAULT_DURATION_MINUTES = 60;

    private static final int FLAGS = Pattern.CASE_INSENSITIVE;

    // ---- Intent patterns ----
    private static final Pattern CREATE_PREFIX = Pattern.compile(
            "^\\s*(?:please\\s+)?(?:(?:can|could|would)\\s+you\\s+)?(?:please\\s+)?"
                    + "(?:add|create|schedule|book|set\\s+up|remind\\s+me\\s+to|new\\s+task)\\b\\s*"
                    + "(?:(?:a|an)\\s+)?(?:new\\s+)?(?:(?:task|reminder|event)\\b\\s*)?"
                    + "(?:(?:called|named|titled|to|for)\\b\\s*|:\\s*)?", FLAGS);
    private static final Pattern FREE_PATTERN = Pattern.compile("\\b(free|available|availability)\\b", FLAGS);
    private static final Pattern BRIEFING_PATTERN = Pattern.compile(
            "\\b(briefing|brief|summary|summari[sz]e|my day|overview)\\b", FLAGS);
    private static final Pattern LIST_PATTERN = Pattern.compile(
            "\\b(list|show|what|display|view|see|get|any)\\b.*\\b(tasks?|schedule|agenda|plans?|to-?dos?|today|tomorrow)\\b"
                    + "|\\bmy\\s+(tasks|schedule|agenda)\\b|\\bwhat\\s+do\\s+i\\s+have\\b", FLAGS);
    private static final Pattern HELP_PATTERN = Pattern.compile(
            "^\\s*(hi|hello|hey|help|good\\s+(morning|afternoon|evening))\\b|what\\s+can\\s+you\\s+do", FLAGS);

    // ---- Detail patterns ----
    private static final Pattern QUOTED_TITLE = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern ISO_DATE = Pattern.compile("(?:\\b(?:on|for)\\s+)?\\b(\\d{4})-(\\d{2})-(\\d{2})\\b", FLAGS);
    private static final Pattern DAY_AFTER_TOMORROW = Pattern.compile("(?:\\b(?:on|for)\\s+)?\\bday\\s+after\\s+tomorrow\\b", FLAGS);
    private static final Pattern TODAY = Pattern.compile("(?:\\bfor\\s+)?\\b(today|tonight)\\b", FLAGS);
    private static final Pattern TOMORROW = Pattern.compile("(?:\\bfor\\s+)?\\btomorrow\\b", FLAGS);
    private static final Pattern WEEKDAY = Pattern.compile(
            "(?:\\b(?:on|this|for)\\s+)?\\b(next\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", FLAGS);

    private static final String TIME = "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?";
    private static final Pattern TIME_RANGE = Pattern.compile(
            "(?:\\b(from|between)\\s+)?\\b" + TIME + "\\s*(?:-|–|\\bto\\b|\\buntil\\b|\\btill\\b|\\band\\b)\\s*" + TIME + "\\b",
            FLAGS);
    private static final Pattern SINGLE_TIME = Pattern.compile("(?:\\b(at|around|by)\\s+)?\\b" + TIME + "\\b", FLAGS);
    private static final Pattern NOON = Pattern.compile("(?:\\bat\\s+)?\\bnoon\\b", FLAGS);
    private static final Pattern DURATION = Pattern.compile(
            "\\bfor\\s+(\\d{1,3})\\s*(minutes?|mins?|m|hours?|hrs?|h)\\b", FLAGS);
    private static final Pattern DURATION_WORDS = Pattern.compile(
            "\\bfor\\s+(half\\s+an\\s+hour|an\\s+hour|one\\s+hour)\\b", FLAGS);
    private static final Pattern PRIORITY = Pattern.compile(
            "(?:\\bwith\\s+)?(?:\\b(?:a|an)\\s+)?\\b(high|medium|normal|low)\\s+priority\\b"
                    + "|\\bpriority\\s*[:=]?\\s*(high|medium|normal|low)\\b|\\b(urgent)\\b", FLAGS);

    private static final Pattern LEADING_FILLER = Pattern.compile(
            "^(?:(?:task|reminder|event)\\b|called\\b|named\\b|titled\\b|to\\b|for\\b|on\\b|at\\b|[:,\\-])\\s*", FLAGS);
    private static final Pattern TRAILING_FILLER = Pattern.compile(
            "\\s*(?:\\b(?:on|at|from|for|by|in|with|and|the|to|between|around)|[,;:.!?\\-])\\s*$", FLAGS);

    private final Clock clock;

    public ChatMessageParser(Clock clock) {
        this.clock = clock;
    }

    public Intent detectIntent(String message) {
        String text = message == null ? "" : message.trim();
        if (CREATE_PREFIX.matcher(text).lookingAt() && !text.isBlank()) {
            return Intent.CREATE_TASK;
        }
        if (FREE_PATTERN.matcher(text).find()) {
            return Intent.FREE_SLOTS;
        }
        if (BRIEFING_PATTERN.matcher(text).find()) {
            return Intent.BRIEFING;
        }
        if (LIST_PATTERN.matcher(text).find()) {
            return Intent.LIST_TASKS;
        }
        if (HELP_PATTERN.matcher(text).find()) {
            return Intent.HELP;
        }
        return Intent.UNKNOWN;
    }

    /** Finds a date in the message ("today", "tomorrow", "friday", "2026-10-20"), or null if none. */
    public LocalDate parseDate(String message) {
        return extractDate(new Text(message));
    }

    /** Finds a duration such as "for 45 minutes" or "for 2 hours", or null if none. */
    public Integer parseDurationMinutes(String message) {
        return extractDuration(new Text(message));
    }

    /** Extracts title, date, times and priority from a "create task" message. */
    public ParsedTaskDetails parseTaskDetails(String message) {
        Text text = new Text(message);

        String quotedTitle = null;
        Matcher quoted = QUOTED_TITLE.matcher(text.value);
        if (quoted.find()) {
            quotedTitle = quoted.group(1).trim();
            text.remove(quoted);
        }
        Matcher prefix = CREATE_PREFIX.matcher(text.value);
        if (prefix.lookingAt()) {
            text.remove(prefix);
        }

        LocalDate date = extractDate(text);
        LocalTime[] range = extractTimeRange(text);
        Integer duration = extractDuration(text);
        LocalTime start = range != null ? range[0] : extractSingleTime(text);
        LocalTime end = range != null ? range[1] : null;
        TaskPriority priority = extractPriority(text);

        boolean endAssumed = false;
        if (start != null && end == null) {
            int minutes = duration != null ? duration : DEFAULT_DURATION_MINUTES;
            endAssumed = duration == null;
            int endMinuteOfDay = start.getHour() * 60 + start.getMinute() + minutes;
            // Tasks must end on the same day; leave end empty so the caller can ask for it.
            end = endMinuteOfDay < 24 * 60 ? LocalTime.of(endMinuteOfDay / 60, endMinuteOfDay % 60) : null;
        }

        String title = quotedTitle != null ? quotedTitle : cleanTitle(text.value);
        return new ParsedTaskDetails(title.isBlank() ? null : title, date, start, end, priority, endAssumed);
    }

    private LocalDate extractDate(Text text) {
        LocalDate today = LocalDate.now(clock);

        Matcher iso = ISO_DATE.matcher(text.value);
        if (iso.find()) {
            text.remove(iso);
            try {
                return LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
                        Integer.parseInt(iso.group(3)));
            } catch (DateTimeException ex) {
                return null;
            }
        }
        Matcher dayAfter = DAY_AFTER_TOMORROW.matcher(text.value);
        if (dayAfter.find()) {
            text.remove(dayAfter);
            return today.plusDays(2);
        }
        Matcher todayMatch = TODAY.matcher(text.value);
        if (todayMatch.find()) {
            text.remove(todayMatch);
            return today;
        }
        Matcher tomorrow = TOMORROW.matcher(text.value);
        if (tomorrow.find()) {
            text.remove(tomorrow);
            return today.plusDays(1);
        }
        Matcher weekday = WEEKDAY.matcher(text.value);
        if (weekday.find()) {
            text.remove(weekday);
            DayOfWeek day = DayOfWeek.valueOf(weekday.group(2).toUpperCase(Locale.ROOT));
            return weekday.group(1) != null
                    ? today.with(TemporalAdjusters.next(day))
                    : today.with(TemporalAdjusters.nextOrSame(day));
        }
        return null;
    }

    private LocalTime[] extractTimeRange(Text text) {
        Matcher m = TIME_RANGE.matcher(text.value);
        while (m.find()) {
            boolean looksLikeTime = m.group(1) != null || m.group(3) != null || m.group(4) != null
                    || m.group(6) != null || m.group(7) != null;
            if (!looksLikeTime) {
                continue;
            }
            String startAmPm = m.group(4);
            String endAmPm = m.group(7);
            LocalTime end = toTime(m.group(5), m.group(6), endAmPm);
            // "3-4pm": the start time borrows "pm" from the end time
            LocalTime start = toTime(m.group(2), m.group(3), startAmPm != null ? startAmPm : endAmPm);
            if (startAmPm == null && endAmPm != null && start != null && end != null && !start.isBefore(end)) {
                start = toTime(m.group(2), m.group(3), "am"); // "11-1pm" means 11am to 1pm
            }
            if (start != null && end != null) {
                text.remove(m);
                return new LocalTime[] {start, end};
            }
        }
        return null;
    }

    private LocalTime extractSingleTime(Text text) {
        Matcher noon = NOON.matcher(text.value);
        if (noon.find()) {
            text.remove(noon);
            return LocalTime.NOON;
        }
        Matcher m = SINGLE_TIME.matcher(text.value);
        while (m.find()) {
            boolean looksLikeTime = m.group(1) != null || m.group(3) != null || m.group(4) != null;
            if (!looksLikeTime) {
                continue;
            }
            LocalTime time = toTime(m.group(2), m.group(3), m.group(4));
            if (time != null) {
                text.remove(m);
                return time;
            }
        }
        return null;
    }

    private Integer extractDuration(Text text) {
        Matcher words = DURATION_WORDS.matcher(text.value);
        if (words.find()) {
            text.remove(words);
            return words.group(1).toLowerCase(Locale.ROOT).startsWith("half") ? 30 : 60;
        }
        Matcher m = DURATION.matcher(text.value);
        if (m.find()) {
            text.remove(m);
            int amount = Integer.parseInt(m.group(1));
            return m.group(2).toLowerCase(Locale.ROOT).startsWith("h") ? amount * 60 : amount;
        }
        return null;
    }

    private TaskPriority extractPriority(Text text) {
        Matcher m = PRIORITY.matcher(text.value);
        if (!m.find()) {
            return null;
        }
        text.remove(m);
        String word = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "high", "urgent" -> TaskPriority.HIGH;
            case "low" -> TaskPriority.LOW;
            default -> TaskPriority.MEDIUM;
        };
    }

    /** Converts hour/minute/am-pm text to a LocalTime, or returns null if it is not a valid time. */
    private static LocalTime toTime(String hourText, String minuteText, String amPm) {
        int hour = Integer.parseInt(hourText);
        int minute = minuteText == null ? 0 : Integer.parseInt(minuteText);
        if (minute > 59) {
            return null;
        }
        if (amPm != null) {
            if (hour < 1 || hour > 12) {
                return null;
            }
            boolean pm = amPm.equalsIgnoreCase("pm");
            if (hour == 12) {
                hour = pm ? 12 : 0;
            } else if (pm) {
                hour += 12;
            }
        } else if (hour > 23) {
            return null;
        }
        return LocalTime.of(hour, minute);
    }

    private static String cleanTitle(String raw) {
        String title = raw.replaceAll("\\s+", " ").trim();
        String previous;
        do {
            previous = title;
            title = LEADING_FILLER.matcher(title).replaceFirst("").trim();
            title = TRAILING_FILLER.matcher(title).replaceFirst("").trim();
        } while (!title.equals(previous));
        if (title.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(title.charAt(0)) + title.substring(1);
    }

    /** Holds the message text while recognised parts are removed from it one by one. */
    private static final class Text {
        private String value;

        Text(String value) {
            this.value = value == null ? "" : value;
        }

        void remove(Matcher match) {
            value = value.substring(0, match.start()) + " " + value.substring(match.end());
        }
    }
}
