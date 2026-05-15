package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.entities.ChatConversation;
import ch.exmachina.cosmo42.repositories.ChatConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatConversationServiceTest {

    ChatConversationRepository repository;
    ChatMemory chatMemory;
    JdbcTemplate jdbcTemplate;
    TitleSanitizer sanitizer;
    TitleGeneratorAdvisor advisor;
    ChatModel chatModel;
    OpenAiChatOptions.Builder titleOptionsBuilder;
    Clock fixedClock;
    ChatConversationService service;

    static final LocalDateTime NOW = LocalDateTime.parse("2026-05-15T12:00:00");

    @BeforeEach
    void setUp() {
        repository = mock(ChatConversationRepository.class);
        chatMemory = mock(ChatMemory.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        sanitizer = new TitleSanitizer();
        advisor = new TitleGeneratorAdvisor();
        advisor.setPromptTemplate("Title for: %s");
        chatModel = mock(ChatModel.class);
        titleOptionsBuilder = OpenAiChatOptions.builder().model("test-model").maxTokens(32);
        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        fixedClock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new ChatConversationService(
                repository, chatMemory, jdbcTemplate, sanitizer, advisor,
                chatModel, titleOptionsBuilder, fixedClock);
    }

    @Test
    void createIfAbsentInsertsNewRowWhenMissing() {
        when(repository.findByUuid("u-1")).thenReturn(Optional.empty());
        when(repository.save(any(ChatConversation.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatConversation created = service.createIfAbsent("u-1");

        ArgumentCaptor<ChatConversation> cap = ArgumentCaptor.forClass(ChatConversation.class);
        verify(repository).save(cap.capture());
        ChatConversation saved = cap.getValue();
        assertThat(saved.getUuid()).isEqualTo("u-1");
        assertThat(saved.getTitle()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(created).isSameAs(saved);
    }

    @Test
    void createIfAbsentReturnsExistingWhenPresent() {
        ChatConversation existing = new ChatConversation();
        existing.setUuid("u-2");
        when(repository.findByUuid("u-2")).thenReturn(Optional.of(existing));

        ChatConversation got = service.createIfAbsent("u-2");

        verify(repository, never()).save(any());
        assertThat(got).isSameAs(existing);
    }

    @Test
    void createIfAbsentRetriesOnUniqueConstraintViolation() {
        ChatConversation racingInsert = new ChatConversation();
        racingInsert.setUuid("u-3");
        when(repository.findByUuid("u-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racingInsert));
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        ChatConversation got = service.createIfAbsent("u-3");

        verify(repository, times(2)).findByUuid("u-3");
        assertThat(got).isSameAs(racingInsert);
    }

    @Test
    void persistGeneratedTitleUpdatesWhenLlmReturnsValidText() {
        when(repository.updateTitleByUuid(eq("u-1"), eq("Clean Title"), eq(NOW))).thenReturn(1);

        service.persistGeneratedTitle("u-1", "  Title: \"Clean Title\"  \n stuff");

        verify(repository).updateTitleByUuid("u-1", "Clean Title", NOW);
    }

    @Test
    void persistGeneratedTitleNoOpOnBlankInput() {
        service.persistGeneratedTitle("u-1", "   ");

        verify(repository, never()).updateTitleByUuid(anyString(), anyString(), any());
    }

    @Test
    void persistGeneratedTitleNoOpOnNullInput() {
        service.persistGeneratedTitle("u-1", null);

        verify(repository, never()).updateTitleByUuid(anyString(), anyString(), any());
    }

    @Test
    void persistGeneratedTitleLogsAndReturnsWhenUuidUnknown() {
        when(repository.updateTitleByUuid(anyString(), anyString(), any())).thenReturn(0);

        service.persistGeneratedTitle("u-missing", "Some Title");

        verify(repository).updateTitleByUuid("u-missing", "Some Title", NOW);
    }

    @Test
    void listDelegatesToRepository() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        var page = new org.springframework.data.domain.PageImpl<ChatConversation>(java.util.List.of());
        when(repository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(page);

        var result = service.list(pageable);

        assertThat(result).isSameAs(page);
    }

    @Test
    void getReturnsConversationWithMessages() {
        ChatConversation c = new ChatConversation();
        c.setUuid("u-1");
        when(repository.findByUuid("u-1")).thenReturn(Optional.of(c));
        var msgs = java.util.List.<org.springframework.ai.chat.messages.Message>of(
                new org.springframework.ai.chat.messages.UserMessage("hi")
        );
        when(chatMemory.get("u-1")).thenReturn(msgs);

        var result = service.get("u-1");

        assertThat(result.conversation()).isSameAs(c);
        assertThat(result.messages()).isSameAs(msgs);
    }

    @Test
    void getThrowsNotFoundWhenMissing() {
        when(repository.findByUuid("missing")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                ch.exmachina.cosmo42.exceptions.ChatConversationNotFoundException.class,
                () -> service.get("missing"));
    }

    @Test
    void renameUpdatesTitleWhenValid() {
        ChatConversation c = new ChatConversation();
        c.setUuid("u-1");
        c.setTitle("old");
        when(repository.updateTitleByUuid("u-1", "New", NOW)).thenReturn(1);
        when(repository.findByUuid("u-1")).thenReturn(Optional.of(c));

        var result = service.rename("u-1", "New");

        verify(repository).updateTitleByUuid("u-1", "New", NOW);
        assertThat(result).isSameAs(c);
    }

    @Test
    void renameThrowsBadArgWhenSanitizerDropsTitle() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.rename("u-1", "   "));

        verify(repository, never()).updateTitleByUuid(anyString(), anyString(), any());
    }

    @Test
    void renameThrowsNotFoundWhenUuidUnknown() {
        when(repository.updateTitleByUuid(eq("missing"), anyString(), any())).thenReturn(0);

        org.junit.jupiter.api.Assertions.assertThrows(
                ch.exmachina.cosmo42.exceptions.ChatConversationNotFoundException.class,
                () -> service.rename("missing", "Some Title"));
    }

    @Test
    void regenerateTitleUsesFirstUserMessage() {
        ChatConversation c = new ChatConversation();
        c.setUuid("u-1");
        var firstUser = new org.springframework.ai.chat.messages.UserMessage("How do I deploy?");
        var assistant = new org.springframework.ai.chat.messages.AssistantMessage("answer");
        var laterUser = new org.springframework.ai.chat.messages.UserMessage("follow-up");
        when(chatMemory.get("u-1"))
                .thenReturn(java.util.List.of(firstUser, assistant, laterUser));
        when(repository.findByUuid("u-1")).thenReturn(Optional.of(c));
        when(repository.updateTitleByUuid(eq("u-1"), anyString(), any())).thenReturn(1);

        org.mockito.ArgumentCaptor<org.springframework.ai.chat.prompt.Prompt> promptCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        when(chatModel.call(promptCaptor.capture())).thenReturn(
                new org.springframework.ai.chat.model.ChatResponse(
                        java.util.List.of(new org.springframework.ai.chat.model.Generation(
                                new org.springframework.ai.chat.messages.AssistantMessage("Deploy Question")
                        ))));

        ChatConversation result = service.regenerateTitle("u-1");

        assertThat(promptCaptor.getValue().getUserMessage().getText())
                .contains("How do I deploy?");
        verify(repository).updateTitleByUuid("u-1", "Deploy Question", NOW);
        assertThat(result).isSameAs(c);
    }

    @Test
    void regenerateTitleThrowsNotFoundWhenConversationMissing() {
        when(repository.findByUuid("missing")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                ch.exmachina.cosmo42.exceptions.ChatConversationNotFoundException.class,
                () -> service.regenerateTitle("missing"));
    }

    @Test
    void regenerateTitleThrowsConflictWhenNoUserMessage() {
        ChatConversation c = new ChatConversation();
        c.setUuid("u-1");
        when(repository.findByUuid("u-1")).thenReturn(Optional.of(c));
        when(chatMemory.get("u-1")).thenReturn(java.util.List.of(
                new org.springframework.ai.chat.messages.AssistantMessage("only assistant")
        ));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.regenerateTitle("u-1"));
    }

    @Test
    void deleteCascadesMessagesThenRow() {
        when(repository.deleteByUuid("u-1")).thenReturn(1);

        service.delete("u-1");

        var inOrder = org.mockito.Mockito.inOrder(jdbcTemplate, repository);
        inOrder.verify(jdbcTemplate).update(
                "DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?", "u-1");
        inOrder.verify(repository).deleteByUuid("u-1");
    }

    @Test
    void deleteThrowsNotFoundWhenUuidUnknown() {
        when(repository.deleteByUuid("missing")).thenReturn(0);

        org.junit.jupiter.api.Assertions.assertThrows(
                ch.exmachina.cosmo42.exceptions.ChatConversationNotFoundException.class,
                () -> service.delete("missing"));
    }
}
