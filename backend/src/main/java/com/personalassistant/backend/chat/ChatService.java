
package com.personalassistant.backend.chat;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.personalassistant.backend.briefing.BriefingResponse;
import com.personalassistant.backend.briefing.BriefingService;
import com.personalassistant.backend.exception.BadRequestException;
import com.personalassistant.backend.exception.ScheduleConflictException;
import com.personalassistant.backend.schedule.ScheduleService;
import com.personalassistant.backend.schedule.TimeSlot;
import com.personalassistant.backend.task.TaskPriority;
import com.personalassistant.backend.task.TaskRequest;
import com.personalassistant.backend.task.TaskResponse;
import com.personalassistant.backend.task.TaskService;
import com.personalassistant.backend.task.TaskStatus;

@Service
public class ChatService {

    private static final String HELP_TEXT = """
            I can help you with:
            - Listing tasks: "What are my tasks today?" or "Show tasks for 2026-10-20"
            - Creating tasks: "Add Team meeting tomorrow from 10:00 to 11:00 high priority"
            - Finding free time: "When am I free tomorrow?"
            - A daily briefing: "Give me my briefing"
            """;

    private final ChatMessageParser parser;
    private final TaskService taskService;
    private final ScheduleService scheduleService;
    private final BriefingService briefingService;
    private final GeminiApiClient geminiApiClient;
    private final Clock clock;

    public ChatService(
            ChatMessageParser parser,
            TaskService taskService,
            ScheduleService scheduleService,
            BriefingService briefingService,
            GeminiApiClient geminiApiClient,
            Clock clock) {
        this.parser = parser;
        this.taskService = taskService;
        this.scheduleService = scheduleService;
        this.briefingService = briefingService;
        this.geminiApiClient = geminiApiClient;
        this.clock = clock;
    }

    public ChatResponse handleMessage(String message) {
        return switch (parser.detectIntent(message)) {
            case CREATE_TASK -> createTask(message);
            case LIST_TASKS -> listTasks(message);
            case FREE_SLOTS -> freeSlots(message);
            case BRIEFING -> briefing(message);
            case HELP, UNKNOWN -> conversationalReply(message);
        };
    }

    private ChatResponse conversationalReply(String message) {
        if (!geminiApiClient.isConfigured()) {
            return ChatResponse.assistantText(
                    "Gemini chat is not configured. Set the GEMINI_API_KEY environment variable and restart the backend.",
                    "AI_UNAVAILABLE", "unavailable");
        }

        try {
            return ChatResponse.assistantText(
                    geminiApiClient.generateReply(message),
                    "CONVERSATION",
                    "gemini");
        } catch (GeminiApiException ex) {
            System.err.println("Gemini error: " + ex.getMessage());
            ex.printStackTrace(System.err);

            return ChatResponse.assistantText(
                    "I couldn't get a response from Gemini right now. Please try again shortly.",
                    "AI_UNAVAILABLE",
                    "unavailable");
        }
    }

    private ChatResponse listTasks(String message) {
        LocalDate date = parser.parseDate(message);
        List<TaskResponse> tasks = date == null
                ? taskService.getTasks(null, null, null)
                : taskService.getTasksForDate(date);
        String when = date == null ? "in total" : describeDate(date);

        if (tasks.isEmpty()) {
            return ChatResponse.of("You have no tasks " + when + ".", "LIST_TASKS", tasks, null, null);
        }

        StringBuilder reply = new StringBuilder("You have " + tasks.size() + " task(s) " + when + ":");
        for (int i = 0; i < tasks.size(); i++) {
            reply.append("\n").append(i + 1).append(". ")
                    .append(formatTask(tasks.get(i), date == null));
        }

        return ChatResponse.of(reply.toString(), "LIST_TASKS", tasks, null, null);
    }

    private ChatResponse createTask(String message) {
        ParsedTaskDetails details = parser.parseTaskDetails(message);

        List<String> missing = new ArrayList<>();
        if (details.title() == null) {
            missing.add("a title");
        }
        if (details.date() == null) {
            missing.add("a date (today, tomorrow, a weekday or yyyy-MM-dd)");
        }
        if (details.startTime() == null) {
            missing.add("a start time (e.g. \"at 3pm\" or \"from 14:00 to 15:00\")");
        } else if (details.endTime() == null) {
            missing.add("an end time on the same day");
        }

        if (!missing.isEmpty()) {
            return ChatResponse.text(
                    "I can create that task, but I still need " + String.join(", ", missing)
                            + ". Example: \"Add Team meeting tomorrow from 10:00 to 11:00 high priority\".",
                    "CREATE_TASK_NEEDS_DETAILS");
        }

        if (details.title().length() > 100) {
            return ChatResponse.text(
                    "That title is too long. Please keep it under 100 characters.",
                    "CREATE_TASK_NEEDS_DETAILS");
        }

        TaskPriority priority = details.priority() == null
                ? TaskPriority.MEDIUM
                : details.priority();

        TaskRequest request = new TaskRequest(
                details.title(), null, details.date(), details.startTime(),
                details.endTime(), priority, TaskStatus.PENDING);

        try {
            TaskResponse created = taskService.createTask(request, false);

            String reply = "Done! I created '" + created.title() + "' "
                    + describeDate(created.date()) + " from "
                    + created.startTime() + " to " + created.endTime()
                    + " (" + created.priority() + " priority).";

            if (details.endTimeAssumed()) {
                reply += " I assumed it lasts "
                        + ChatMessageParser.DEFAULT_DURATION_MINUTES
                        + " minutes because no end time was given.";
            }

            return ChatResponse.of(reply, "CREATE_TASK", List.of(created), created, null);

        } catch (ScheduleConflictException ex) {
            StringBuilder reply = new StringBuilder(
                    "I did not create '" + details.title() + "' because "
                            + details.startTime() + "-" + details.endTime() + " overlaps: ");

            reply.append(String.join(", ", ex.getConflicts().stream()
                    .map(t -> "'" + t.title() + "' (" + t.startTime() + "-" + t.endTime() + ")")
                    .toList()));
            reply.append(".");

            if (ex.getSuggestedSlots().isEmpty()) {
                reply.append(" I couldn't find a free slot of the same length that day.");
            } else {
                reply.append(" Free alternatives: ")
                        .append(joinSlots(ex.getSuggestedSlots())).append(".");
            }

            return ChatResponse.of(
                    reply.toString(), "SCHEDULE_CONFLICT",
                    ex.getConflicts(), null, ex.getSuggestedSlots());

        } catch (BadRequestException ex) {
            return ChatResponse.text(
                    "I couldn't create that task: " + ex.getMessage() + ".",
                    "CREATE_TASK_NEEDS_DETAILS");
        }
    }

    private ChatResponse freeSlots(String message) {
        LocalDate date = parser.parseDate(message);
        if (date == null) {
            date = LocalDate.now(clock);
        }

        Integer requested = parser.parseDurationMinutes(message);
        int duration = requested == null ? 30 : Math.max(5, Math.min(requested, 720));

        List<TimeSlot> slots = scheduleService.findFreeSlots(date, duration, null);

        String reply = slots.isEmpty()
                ? "You have no free slots of at least " + duration + " minutes " + describeDate(date) + "."
                : "You are free " + describeDate(date) + " at: " + joinSlots(slots) + ".";

        return ChatResponse.of(reply, "FREE_SLOTS", null, null, slots);
    }

    private ChatResponse briefing(String message) {
        BriefingResponse briefing = briefingService.getBriefing(parser.parseDate(message));
        return ChatResponse.of(
                briefing.greeting() + " " + briefing.summary(),
                "BRIEFING", briefing.tasks(), null, briefing.freeSlots());
    }

    private String describeDate(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date.equals(today)) {
            return "today";
        }
        if (date.equals(today.plusDays(1))) {
            return "tomorrow";
        }
        return "on " + date;
    }

    private static String formatTask(TaskResponse task, boolean includeDate) {
        StringBuilder text = new StringBuilder();

        if (includeDate) {
            text.append(task.date()).append(" ");
        }

        if (task.startTime() != null) {
            text.append(task.startTime()).append("-").append(task.endTime()).append(" ");
        } else {
            text.append("(no time) ");
        }

        text.append(task.title())
                .append(" [").append(task.priority())
                .append(", ").append(task.status()).append("]");

        return text.toString();
    }

    private static String joinSlots(List<TimeSlot> slots) {
        return String.join(", ", slots.stream().map(TimeSlot::toString).toList());
    }
}
