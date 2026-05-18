package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.BaseTest;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IngestionJobRecoveryServiceTest extends BaseTest {

    @Mock
    IngestionJobService ingestionJobService;
    @Mock
    KBDocumentIngestionProcessor ingestionProcessor;

    @InjectMocks
    IngestionJobRecoveryService recoveryService;

    @Test
    void onApplicationReady_requeueStuckJobs() {
        IngestionJob job1 = jobWithUuid("uuid-1");
        IngestionJob job2 = jobWithUuid("uuid-2");
        when(ingestionJobService.findByStatuses(anyList())).thenReturn(List.of(job1, job2));

        recoveryService.onApplicationReady();

        verify(ingestionProcessor).processAsync("uuid-1");
        verify(ingestionProcessor).processAsync("uuid-2");
        verify(ingestionJobService, never()).markInterrupted(anyString());
    }

    @Test
    void onApplicationReady_isIdempotent_secondCallIgnored() {
        when(ingestionJobService.findByStatuses(anyList())).thenReturn(List.of());

        recoveryService.onApplicationReady();
        recoveryService.onApplicationReady();

        verify(ingestionJobService, times(1)).findByStatuses(anyList());
    }

    @Test
    void onApplicationReady_noStuckJobs_noRequeue() {
        when(ingestionJobService.findByStatuses(anyList())).thenReturn(List.of());

        recoveryService.onApplicationReady();

        verifyNoInteractions(ingestionProcessor);
    }

    @Test
    void scheduledRecovery_beforeFirstStartup_isNoOp() {
        recoveryService.scheduledRecovery();

        verifyNoInteractions(ingestionJobService);
    }

    @Test
    void scheduledRecovery_afterStartup_recoversStuckJobs() {
        when(ingestionJobService.findByStatuses(anyList())).thenReturn(List.of());
        recoveryService.onApplicationReady();

        IngestionJob job = jobWithUuid("uuid-3");
        when(ingestionJobService.findByStatuses(List.of(
                IngestionJobStatus.PENDING, IngestionJobStatus.PROCESSING, IngestionJobStatus.INTERRUPTED)))
                .thenReturn(List.of(job));

        recoveryService.scheduledRecovery();

        verify(ingestionProcessor).processAsync("uuid-3");
        verify(ingestionJobService, never()).markInterrupted(anyString());
    }

    private IngestionJob jobWithUuid(String uuid) {
        IngestionJob job = new IngestionJob();
        job.setUuid(uuid);
        return job;
    }

}
