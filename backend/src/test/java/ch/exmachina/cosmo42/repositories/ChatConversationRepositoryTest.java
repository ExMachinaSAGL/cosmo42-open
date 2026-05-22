package ch.exmachina.cosmo42.repositories;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import ch.exmachina.cosmo42.entities.ChatConversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ChatConversationRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    ChatConversationRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void saveAndFindByUuid() {
        ChatConversation c = newConversation("First chat");
        repository.save(c);

        Optional<ChatConversation> found = repository.findByUuid(c.getUuid());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("First chat");
    }

    @Test
    void findByUuidReturnsEmptyWhenMissing() {
        assertThat(repository.findByUuid("00000000-0000-0000-0000-000000000000")).isEmpty();
    }

    @Test
    void findAllByOrderByUpdatedAtDescPaginates() {
        LocalDateTime now = LocalDateTime.now();
        IntStream.range(0, 5).forEachOrdered(i -> {
            ChatConversation c = newConversation("Chat " + i);
            c.setCreatedAt(now.minusMinutes(i));
            c.setUpdatedAt(now.minusMinutes(i));
            repository.save(c);
        });

        Page<ChatConversation> page = repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 3));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Chat 0");
        assertThat(page.getContent().get(2).getTitle()).isEqualTo("Chat 2");
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void updateTitleByUuidUpdatesTitleAndTimestamp() {
        ChatConversation c = newConversation(null);
        repository.save(c);
        LocalDateTime newTs = LocalDateTime.now().plusHours(1);

        int updated = repository.updateTitleByUuid(c.getUuid(), "Renamed", newTs);

        assertThat(updated).isEqualTo(1);
        ChatConversation refreshed = repository.findByUuid(c.getUuid()).orElseThrow();
        assertThat(refreshed.getTitle()).isEqualTo("Renamed");
        assertThat(refreshed.getUpdatedAt()).isEqualToIgnoringNanos(newTs);
    }

    @Test
    void updateTitleByUuidReturnsZeroWhenUuidMissing() {
        int updated = repository.updateTitleByUuid(
                "00000000-0000-0000-0000-000000000000",
                "anything",
                LocalDateTime.now());

        assertThat(updated).isEqualTo(0);
    }

    @Test
    void updateActivityByUuidUpdatesTimestampOnly() {
        ChatConversation c = newConversation("Still titled");
        repository.save(c);
        LocalDateTime newTs = LocalDateTime.now().plusHours(1);

        int updated = repository.updateActivityByUuid(c.getUuid(), newTs);

        assertThat(updated).isEqualTo(1);
        ChatConversation refreshed = repository.findByUuid(c.getUuid()).orElseThrow();
        assertThat(refreshed.getTitle()).isEqualTo("Still titled");
        assertThat(refreshed.getUpdatedAt()).isEqualToIgnoringNanos(newTs);
    }

    @Test
    void deleteByUuidReturnsAffectedRows() {
        ChatConversation c = newConversation("doomed");
        repository.save(c);

        int deleted = repository.deleteByUuid(c.getUuid());

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findByUuid(c.getUuid())).isEmpty();
    }

    private ChatConversation newConversation(String title) {
        ChatConversation c = new ChatConversation();
        c.setUuid(UUID.randomUUID().toString());
        c.setTitle(title);
        LocalDateTime now = LocalDateTime.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return c;
    }
}
