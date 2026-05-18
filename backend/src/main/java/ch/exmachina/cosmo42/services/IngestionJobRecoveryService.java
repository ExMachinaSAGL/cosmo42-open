package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@RequiredArgsConstructor
public class IngestionJobRecoveryService {

    private static final List<IngestionJobStatus> STUCK_STATUSES = List.of(
            IngestionJobStatus.PENDING,
            IngestionJobStatus.PROCESSING,
            IngestionJobStatus.INTERRUPTED);

    private final IngestionJobService ingestionJobService;
    private final KBDocumentIngestionProcessor ingestionProcessor;
    private final AtomicBoolean recovered = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!recovered.compareAndSet(false, true)) return;
        recoverInterruptedJobs();
    }

    @Scheduled(fixedDelayString = "${cosmo42.ingestion.recovery.interval-ms:300000}",
               initialDelayString = "${cosmo42.ingestion.recovery.interval-ms:300000}")
    public void scheduledRecovery() {
        if (!recovered.get()) return;
        recoverInterruptedJobs();
    }

    private void recoverInterruptedJobs() {
        List<IngestionJob> stuckJobs = ingestionJobService.findByStatuses(STUCK_STATUSES);

        if (stuckJobs.isEmpty()) {
            log.info("No interrupted ingestion jobs found.");
            return;
        }

        log.warn("Found {} interrupted ingestion job(s). Re-queuing for resume.", stuckJobs.size());
        for (IngestionJob job : stuckJobs) {
            ingestionProcessor.processAsync(job.getUuid());
        }
    }
}
