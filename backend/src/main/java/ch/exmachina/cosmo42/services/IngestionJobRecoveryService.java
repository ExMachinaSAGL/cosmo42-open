package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class IngestionJobRecoveryService implements ApplicationListener<ContextRefreshedEvent> {

    private final IngestionJobService ingestionJobService;
    private final KBDocumentIngestionProcessor ingestionProcessor;
    private final AtomicBoolean recovered = new AtomicBoolean(false);

    public IngestionJobRecoveryService(IngestionJobService ingestionJobService,
                                       KBDocumentIngestionProcessor ingestionProcessor) {
        this.ingestionJobService = ingestionJobService;
        this.ingestionProcessor = ingestionProcessor;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
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
        List<IngestionJob> stuckJobs = ingestionJobService.findByStatuses(
                List.of(IngestionJobStatus.PENDING, IngestionJobStatus.PROCESSING, IngestionJobStatus.INTERRUPTED));

        if (stuckJobs.isEmpty()) {
            log.info("No interrupted ingestion jobs found on startup.");
            return;
        }

        log.warn("Found {} interrupted ingestion job(s) on startup. Re-queuing for resume.", stuckJobs.size());
        for (IngestionJob job : stuckJobs) {
            ingestionJobService.markInterrupted(job.getUuid());
            ingestionProcessor.processAsync(job.getUuid());
        }
    }
}
