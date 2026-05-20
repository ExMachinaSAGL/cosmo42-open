package ch.exmachina.cosmo42.services.chat;

import ch.exmachina.cosmo42.AbstractIntegrationTest;
import ch.exmachina.cosmo42.entities.ChatConversation;
import ch.exmachina.cosmo42.repositories.ChatConversationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CreateIfAbsentConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREAD_COUNT = 50;

    @Autowired ChatConversationService service;
    @Autowired ChatConversationRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    ExecutorService executor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM SPRING_AI_CHAT_MEMORY");
        jdbcTemplate.update("DELETE FROM chat_conversation");
        executor = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    void fiftyThreadsRacingOnSameUuidProduceExactlyOneRow() throws Exception {
        String sharedUuid = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);

        List<Future<RaceResult>> futures = new ArrayList<>(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    ChatConversation result = service.createIfAbsent(sharedUuid);
                    return RaceResult.success(result);
                } catch (RuntimeException e) {
                    return RaceResult.failure(e);
                }
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        List<RaceResult> results = new ArrayList<>(THREAD_COUNT);
        for (Future<RaceResult> f : futures) {
            results.add(f.get(15, TimeUnit.SECONDS));
        }

        long rowCount = repository.findAll().stream()
                .filter(c -> c.getUuid().equals(sharedUuid))
                .count();
        assertThat(rowCount)
                .as("Exactly one chat_conversation row must exist after %d concurrent createIfAbsent calls",
                        THREAD_COUNT)
                .isEqualTo(1);

        long succeeded = results.stream().filter(r -> r.success).count();
        assertThat(succeeded)
                .as("Each thread must either return the existing/created conversation or surface a "
                        + "well-defined error — at least one must succeed.")
                .isGreaterThanOrEqualTo(1);

        results.stream()
                .filter(r -> r.success)
                .forEach(r -> assertThat(r.conversation.getUuid()).isEqualTo(sharedUuid));
    }

    @Test
    void allFiftyThreadsObserveThePersistedRowOnceTheRaceIsResolved() throws Exception {
        // Run two waves: first wave races; second wave should hit the "already present" fast path
        // for every thread. This pins the documented behavior that createIfAbsent is idempotent
        // after the row exists.
        String sharedUuid = UUID.randomUUID().toString();

        // Wave 1 — seed via a single call.
        service.createIfAbsent(sharedUuid);
        assertThat(repository.findByUuid(sharedUuid)).isPresent();

        // Wave 2 — race 50 threads, all expected to find the existing row.
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ChatConversation>> futures = new ArrayList<>(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                return service.createIfAbsent(sharedUuid);
            }));
        }
        start.countDown();

        for (Future<ChatConversation> f : futures) {
            ChatConversation result = f.get(10, TimeUnit.SECONDS);
            assertThat(result.getUuid()).isEqualTo(sharedUuid);
        }

        long rowCount = repository.findAll().stream()
                .filter(c -> c.getUuid().equals(sharedUuid))
                .count();
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void differentUuidsConcurrentlyProduceDistinctRows() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ChatConversation>> futures = new ArrayList<>(THREAD_COUNT);
        List<String> uuids = new ArrayList<>(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            String uuid = UUID.randomUUID().toString();
            uuids.add(uuid);
            futures.add(executor.submit(() -> {
                start.await();
                return service.createIfAbsent(uuid);
            }));
        }
        start.countDown();

        for (Future<ChatConversation> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }

        long persistedCount = repository.findAll().stream()
                .filter(c -> uuids.contains(c.getUuid()))
                .count();
        assertThat(persistedCount).isEqualTo(THREAD_COUNT);
    }

    private record RaceResult(boolean success, ChatConversation conversation, RuntimeException error) {
        static RaceResult success(ChatConversation c) {
            return new RaceResult(true, c, null);
        }
        static RaceResult failure(RuntimeException e) {
            return new RaceResult(false, null, e);
        }
    }
}
