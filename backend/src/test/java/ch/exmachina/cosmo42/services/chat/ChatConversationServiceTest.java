package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.entities.ChatConversation;
import ch.exmachina.cosmo42.exceptions.ChatConversationNotFoundException;
import ch.exmachina.cosmo42.exceptions.InvalidChatTitleException;
import ch.exmachina.cosmo42.repositories.ChatConversationRepository;
import ch.exmachina.cosmo42.testsupport.FakeClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatConversationServiceTest {

    ChatConversationRepository repository;
    ChatMemory chatMemory;
    EntityManager entityManager;
    ChatConversationService service;

    static final LocalDateTime NOW = LocalDateTime.parse("2026-05-15T12:00:00");

    @BeforeEach
    void setUp() {
        repository = mock(ChatConversationRepository.class);
        chatMemory = mock(ChatMemory.class);
        entityManager = mock(EntityManager.class);
        service = new ChatConversationService(
                repository, chatMemory, new TitleSanitizer(), FakeClock.fixedAt(NOW), entityManager);
    }

    @Nested
    class CreateIfAbsent {

        @Test
        void insertsNewRowWhenMissing() {
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
        void returnsExistingWhenPresent() {
            ChatConversation existing = new ChatConversation();
            existing.setUuid("u-2");
            when(repository.findByUuid("u-2")).thenReturn(Optional.of(existing));

            ChatConversation got = service.createIfAbsent("u-2");

            verify(repository, never()).save(any());
            assertThat(got).isSameAs(existing);
        }

        @Test
        void retriesOnUniqueConstraintViolation() {
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
        void propagatesExceptionWhenRetryAlsoFails() {
            when(repository.findByUuid("u-double-fail"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.empty());
            when(repository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

            assertThatThrownBy(() -> service.createIfAbsent("u-double-fail"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessage("dup");

            verify(repository, times(2)).findByUuid("u-double-fail");
        }
    }

    @Nested
    class PersistTitle {

        @Test
        void updatesWhenLlmReturnsValidText() {
            when(repository.updateTitleByUuid(eq("u-1"), eq("Clean Title"), eq(NOW))).thenReturn(1);

            var persisted = service.persistGeneratedTitle("u-1", "  Title: \"Clean Title\"  \n stuff");

            verify(repository).updateTitleByUuid("u-1", "Clean Title", NOW);
            assertThat(persisted).contains("Clean Title");
        }

        @Test
        void noOpOnBlankInput() {
            var persisted = service.persistGeneratedTitle("u-1", "   ");

            verify(repository, never()).updateTitleByUuid(anyString(), anyString(), any());
            assertThat(persisted).isEmpty();
        }

        @Test
        void noOpOnNullInput() {
            var persisted = service.persistGeneratedTitle("u-1", null);

            verify(repository, never()).updateTitleByUuid(anyString(), anyString(), any());
            assertThat(persisted).isEmpty();
        }

        @Test
        void logsAndReturnsWhenUuidUnknown() {
            when(repository.updateTitleByUuid(anyString(), anyString(), any())).thenReturn(0);

            var persisted = service.persistGeneratedTitle("u-missing", "Some Title");

            verify(repository).updateTitleByUuid("u-missing", "Some Title", NOW);
            assertThat(persisted).isEmpty();
        }
    }

    @Nested
    class Query {

        @Test
        void listDelegatesToRepository() {
            var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
            var page = new org.springframework.data.domain.PageImpl<ChatConversation>(java.util.List.of());
            when(repository.findAllByOrderByUpdatedAtDesc(pageable)).thenReturn(page);

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

            assertThatThrownBy(() -> service.get("missing"))
                    .isInstanceOf(ChatConversationNotFoundException.class);
        }
    }

    @Nested
    class Modification {

        @Test
        void markActiveUpdatesConversationTimestamp() {
            when(repository.updateActivityByUuid("u-1", NOW)).thenReturn(1);

            service.markActive("u-1");

            verify(repository).updateActivityByUuid("u-1", NOW);
        }

        @Test
        void markActiveThrowsNotFoundWhenUuidUnknown() {
            when(repository.updateActivityByUuid("missing", NOW)).thenReturn(0);

            assertThatThrownBy(() -> service.markActive("missing"))
                    .isInstanceOf(ChatConversationNotFoundException.class);
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
        void renameThrowsInvalidTitleWhenSanitizerDropsTitle() {
            assertThatThrownBy(() -> service.rename("u-1", "   "))
                    .isInstanceOf(InvalidChatTitleException.class);

            verify(repository, never()).updateTitleByUuid(anyString(), anyString(), any());
        }

        @Test
        void renameThrowsNotFoundWhenUuidUnknown() {
            when(repository.updateTitleByUuid(eq("missing"), anyString(), any())).thenReturn(0);

            assertThatThrownBy(() -> service.rename("missing", "Some Title"))
                    .isInstanceOf(ChatConversationNotFoundException.class);
        }
    }

    @Nested
    class Deletion {

        @Test
        void removesRowThenClearsChatMemory() {
            when(repository.deleteByUuid("u-1")).thenReturn(1);

            service.delete("u-1");

            var inOrder = org.mockito.Mockito.inOrder(repository, chatMemory);
            inOrder.verify(repository).deleteByUuid("u-1");
            inOrder.verify(chatMemory).clear("u-1");
        }

        @Test
        void throwsNotFoundWhenUuidUnknownAndDoesNotTouchChatMemory() {
            when(repository.deleteByUuid("missing")).thenReturn(0);

            assertThatThrownBy(() -> service.delete("missing"))
                    .isInstanceOf(ChatConversationNotFoundException.class);

            verify(chatMemory, never()).clear(anyString());
        }
    }
}
