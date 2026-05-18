package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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

    @Scheduled(fixedDelayString = "${cosmo42.ingestion.recovery.interval-ms:300000}",
               initialDelayString = "${cosmo42.ingestion.recovery.interval-ms:300000}")
    public void scheduledRecovery() {
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
