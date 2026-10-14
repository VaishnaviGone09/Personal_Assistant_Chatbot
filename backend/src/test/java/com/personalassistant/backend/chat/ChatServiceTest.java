package com.personalassistant.backend.chat;

import static com.personalassistant.backend.TestClockConfig.fixedClock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.personalassistant.backend.briefing.BriefingService;
import com.personalassistant.backend.schedule.ScheduleService;
import com.personalassistant.backend.task.TaskService;

class ChatServiceTest {

    private GeminiApiClient geminiApiClient;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        geminiApiClient = mock(GeminiApiClient.class);
        chatService = new ChatService(new ChatMessageParser(fixedClock()), mock(TaskService.class),
                mock(ScheduleService.class), mock(BriefingService.class), geminiApiClient, fixedClock());
    }

    @Test
    void usesGeminiForConversationalMessages() {
        when(geminiApiClient.isConfigured()).thenReturn(true);
        when(geminiApiClient.generateReply("Tell me a fun fact")).thenReturn("Octopuses have three hearts.");

        ChatResponse response = chatService.handleMessage("Tell me a fun fact");

        assertThat(response.reply()).isEqualTo("Octopuses have three hearts.");
        assertThat(response.intent()).isEqualTo("CONVERSATION");
        assertThat(response.source()).isEqualTo("gemini");
        assertThat(response.tasks()).isEmpty();
        assertThat(response.createdTask()).isNull();
        assertThat(response.suggestedSlots()).isEmpty();
    }

    @Test
    void returnsExplicitUnavailableResponseWhenGeminiFails() {
        when(geminiApiClient.isConfigured()).thenReturn(true);
        when(geminiApiClient.generateReply("hello")).thenThrow(new GeminiApiException("provider details"));

        ChatResponse response = chatService.handleMessage("hello");

        assertThat(response.source()).isEqualTo("unavailable");
        assertThat(response.intent()).isEqualTo("AI_UNAVAILABLE");
        assertThat(response.reply()).contains("try again shortly").doesNotContain("provider details");
    }

    @Test
    void keepsTaskCreationMessagesOnJavaPath() {
        ChatResponse response = chatService.handleMessage("Add a dentist appointment");

        assertThat(response.intent()).isEqualTo("CREATE_TASK_NEEDS_DETAILS");
        assertThat(response.source()).isEqualTo("rule-based");
        verify(geminiApiClient, never()).generateReply("Add a dentist appointment");
    }
}