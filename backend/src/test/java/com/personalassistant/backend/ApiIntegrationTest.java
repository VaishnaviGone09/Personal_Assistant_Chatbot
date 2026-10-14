package com.personalassistant.backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalassistant.backend.task.TaskRepository;

/** End-to-end tests of the REST API. Today is frozen at Wednesday 2026-10-14 09:00 (see TestClockConfig). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void clearDatabase() {
        taskRepository.deleteAll();
    }

    // ---------- Health ----------

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }

    // ---------- Task CRUD ----------

    @Test
    void fullTaskLifecycle() throws Exception {
        String body = """
                {"title":"  Team meeting ","description":"Weekly sync","date":"2026-10-15",
                 "startTime":"10:00","endTime":"11:00","priority":"high"}
                """;
        JsonNode created = json(mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/tasks/")))
                .andExpect(jsonPath("$.title").value("Team meeting"))
                .andExpect(jsonPath("$.date").value("2026-10-15"))
                .andExpect(jsonPath("$.startTime").value("10:00"))
                .andExpect(jsonPath("$.endTime").value("11:00"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("PENDING")));
        long id = created.get("id").asLong();

        mockMvc.perform(get("/api/tasks/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Weekly sync"));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        String update = """
                {"title":"Team meeting (moved)","date":"2026-10-15","startTime":"14:00","endTime":"15:30",
                 "priority":"MEDIUM","status":"IN_PROGRESS"}
                """;
        mockMvc.perform(put("/api/tasks/" + id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Team meeting (moved)"))
                .andExpect(jsonPath("$.startTime").value("14:00"))
                .andExpect(jsonPath("$.endTime").value("15:30"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/tasks/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(delete("/api/tasks/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task with id " + id + " was not found"));
    }

    @Test
    void listCanBeFilteredByDateStatusAndPriority() throws Exception {
        createTask("A", "2026-10-15", "09:00", "10:00", "HIGH");
        createTask("B", "2026-10-15", "11:00", "12:00", "LOW");
        createTask("C", "2026-10-16", "09:00", "10:00", "HIGH");

        mockMvc.perform(get("/api/tasks").param("date", "2026-10-15"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("A"));
        mockMvc.perform(get("/api/tasks").param("priority", "HIGH")).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/tasks").param("status", "COMPLETED")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void taskWithoutTimesIsAllowed() throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Pay bills\",\"date\":\"2026-10-15\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").doesNotExist())
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    // ---------- Validation and errors ----------

    @Test
    void missingTitleAndDateReturnFieldErrors() throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.details.fieldErrors.date").exists());
    }

    @Test
    void endTimeBeforeStartTimeIsRejected() throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"date\":\"2026-10-15\",\"startTime\":\"11:00\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("endTime must be after startTime"));

        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"date\":\"2026-10-15\",\"startTime\":\"11:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Provide both startTime and endTime, or neither of them"));
    }

    @Test
    void badJsonValuesGiveHelpfulMessages() throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"date\":\"15/10/2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("'date'")))
                .andExpect(jsonPath("$.message", containsString("yyyy-MM-dd")));

        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"date\":\"2026-10-15\",\"priority\":\"SUPER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("[LOW, MEDIUM, HIGH]")));

        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/tasks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("'id'")));
    }

    @Test
    void unknownEndpointReturnsJson404() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------- Schedule conflicts ----------

    @Test
    void overlappingTaskIsRejectedWithConflictsAndFreeSuggestions() throws Exception {
        createTask("Busy morning", "2026-10-15", "08:00", "12:00", "MEDIUM");

        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Call\",\"date\":\"2026-10-15\",\"startTime\":\"11:00\",\"endTime\":\"12:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.conflicts", hasSize(1)))
                .andExpect(jsonPath("$.details.conflicts[0].title").value("Busy morning"))
                .andExpect(jsonPath("$.details.suggestedSlots", hasSize(3)))
                .andExpect(jsonPath("$.details.suggestedSlots[0].startTime").value("12:00"))
                .andExpect(jsonPath("$.details.suggestedSlots[0].endTime").value("13:00"));

        // Saving anyway is possible when the client explicitly asks for it
        mockMvc.perform(post("/api/tasks").param("allowConflict", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Call\",\"date\":\"2026-10-15\",\"startTime\":\"11:00\",\"endTime\":\"12:00\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void updatingATaskDoesNotConflictWithItself() throws Exception {
        long id = createTask("Meeting", "2026-10-15", "10:00", "11:00", "MEDIUM");
        mockMvc.perform(put("/api/tasks/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Meeting\",\"date\":\"2026-10-15\",\"startTime\":\"10:30\",\"endTime\":\"11:30\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void scheduleCheckAndFreeSlotEndpoints() throws Exception {
        createTask("Lunch", "2026-10-15", "12:00", "13:00", "LOW");

        mockMvc.perform(get("/api/schedule/check").param("date", "2026-10-15")
                        .param("startTime", "12:30").param("endTime", "13:30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConflict").value(true))
                .andExpect(jsonPath("$.conflicts[0].title").value("Lunch"))
                .andExpect(jsonPath("$.suggestedSlots[0].durationMinutes").value(60));

        mockMvc.perform(get("/api/schedule/check").param("date", "2026-10-15")
                        .param("startTime", "13:00").param("endTime", "14:00"))
                .andExpect(jsonPath("$.hasConflict").value(false))
                .andExpect(jsonPath("$.suggestedSlots", hasSize(0)));

        mockMvc.perform(get("/api/schedule/free-slots").param("date", "2026-10-15").param("durationMinutes", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freeSlots", hasSize(2)))
                .andExpect(jsonPath("$.freeSlots[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.freeSlots[0].endTime").value("12:00"))
                .andExpect(jsonPath("$.freeSlots[1].startTime").value("13:00"))
                .andExpect(jsonPath("$.freeSlots[1].endTime").value("20:00"));

        mockMvc.perform(get("/api/schedule/free-slots").param("date", "2026-10-15").param("durationMinutes", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/schedule/free-slots"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'date'"));
    }

    // ---------- Today and briefing ----------

    @Test
    void todayEndpointOnlyReturnsTodaysTasks() throws Exception {
        createTask("Today task", "2026-10-14", "10:00", "11:00", "HIGH");
        createTask("Tomorrow task", "2026-10-15", "10:00", "11:00", "HIGH");

        mockMvc.perform(get("/api/tasks/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Today task"));
    }

    @Test
    void briefingSummarisesTheDay() throws Exception {
        createTask("Standup", "2026-10-14", "10:00", "10:30", "HIGH");
        long done = createTask("Emails", "2026-10-14", "11:00", "11:30", "LOW");
        mockMvc.perform(patch("/api/tasks/" + done + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/briefing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-14"))
                .andExpect(jsonPath("$.greeting").value("Good morning!"))
                .andExpect(jsonPath("$.totalTasks").value(2))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.openTasks").value(1))
                .andExpect(jsonPath("$.highPriorityOpenTasks").value(1))
                .andExpect(jsonPath("$.nextTask.title").value("Standup"))
                .andExpect(jsonPath("$.freeSlots[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.summary", containsString("Next up: 'Standup' at 10:00")));

        mockMvc.perform(get("/api/briefing").param("date", "2026-10-20"))
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.summary", containsString("no tasks")));
    }

    // ---------- Chat ----------

    @Test
    void chatCreatesTaskWhenDetailsAreComplete() throws Exception {
        chat("Add Team meeting tomorrow from 10:00 to 11:00 high priority")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CREATE_TASK"))
                .andExpect(jsonPath("$.source").value("rule-based"))
                .andExpect(jsonPath("$.createdTask.title").value("Team meeting"))
                .andExpect(jsonPath("$.createdTask.date").value("2026-10-15"))
                .andExpect(jsonPath("$.createdTask.priority").value("HIGH"))
                .andExpect(jsonPath("$.reply", containsString("Done!")));

        mockMvc.perform(get("/api/tasks").param("date", "2026-10-15"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].startTime").value("10:00"));
    }

    @Test
    void chatAsksForMissingDetailsAndCreatesNothing() throws Exception {
        chat("Add Team meeting")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CREATE_TASK_NEEDS_DETAILS"))
                .andExpect(jsonPath("$.reply", containsString("a date")))
                .andExpect(jsonPath("$.reply", containsString("a start time")))
                .andExpect(jsonPath("$.createdTask").doesNotExist());

        mockMvc.perform(get("/api/tasks")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void chatReportsConflictsWithSuggestions() throws Exception {
        createTask("Doctor", "2026-10-15", "10:00", "11:00", "HIGH");

        chat("Add Gym tomorrow at 10:30")
                .andExpect(jsonPath("$.intent").value("SCHEDULE_CONFLICT"))
                .andExpect(jsonPath("$.reply", containsString("Doctor")))
                .andExpect(jsonPath("$.suggestedSlots", hasSize(3)));

        mockMvc.perform(get("/api/tasks")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void chatListsTasks() throws Exception {
        createTask("Standup", "2026-10-14", "10:00", "10:30", "HIGH");

        chat("What are my tasks today?")
                .andExpect(jsonPath("$.intent").value("LIST_TASKS"))
                .andExpect(jsonPath("$.tasks", hasSize(1)))
                .andExpect(jsonPath("$.reply", containsString("10:00-10:30 Standup")));

        chat("show tasks for tomorrow")
                .andExpect(jsonPath("$.tasks", hasSize(0)))
                .andExpect(jsonPath("$.reply").value("You have no tasks tomorrow."));
    }

    @Test
    void chatFreeTimeBriefingHelpAndValidation() throws Exception {
        chat("When am I free tomorrow?")
                .andExpect(jsonPath("$.intent").value("FREE_SLOTS"))
                .andExpect(jsonPath("$.suggestedSlots[0].startTime").value("08:00"));
        chat("Give me my briefing").andExpect(jsonPath("$.intent").value("BRIEFING"));
        chat("hello").andExpect(jsonPath("$.intent").value("HELP"));
        chat("xyz").andExpect(jsonPath("$.intent").value("UNKNOWN"));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.message").value("Message is required"));
    }

    // ---------- CORS ----------

    @Test
    void corsAllowsConfiguredOriginOnly() throws Exception {
        mockMvc.perform(options("/api/tasks")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));

        mockMvc.perform(options("/api/tasks")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    // ---------- Helpers ----------

    private long createTask(String title, String date, String start, String end, String priority) throws Exception {
        String body = """
                {"title":"%s","date":"%s","startTime":"%s","endTime":"%s","priority":"%s"}
                """.formatted(title, date, start, end, priority);
        return json(mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions chat(String message) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("message", message));
        return mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
