package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionJobRecoveryServiceTest {

    @Mock
    IngestionJobService ingestionJobService;
    @Mock
    KBDocumentIngestionProcessor ingestionProcessor;

    @InjectMocks
    IngestionJobRecoveryService recoveryService;

    @Test
    void scheduledRecovery_requeueStuckJobs() {
        IngestionJob job1 = jobWithUuid("uuid-1");
        IngestionJob job2 = jobWithUuid("uuid-2");
        when(ingestionJobService.findByStatuses(anyList())).thenReturn(List.of(job1, job2));

        recoveryService.scheduledRecovery();

        verify(ingestionProcessor).processAsync("uuid-1");
        verify(ingestionProcessor).processAsync("uuid-2");
        verify(ingestionJobService, never()).markInterrupted(anyString());
    }

    @Test
    void scheduledRecovery_noStuckJobs_noRequeue() {
        when(ingestionJobService.findByStatuses(anyList())).thenReturn(List.of());

        recoveryService.scheduledRecovery();

        verifyNoInteractions(ingestionProcessor);
    }

    @Test
    void scheduledRecovery_requeuesCorrectStatuses() {
        when(ingestionJobService.findByStatuses(List.of(
                IngestionJobStatus.PENDING, IngestionJobStatus.INTERRUPTED)))
                .thenReturn(List.of(jobWithUuid("uuid-3")));

        recoveryService.scheduledRecovery();

        verify(ingestionProcessor).processAsync("uuid-3");
    }

    private IngestionJob jobWithUuid(String uuid) {
        IngestionJob job = new IngestionJob();
        job.setUuid(uuid);
        return job;
    }

}
