package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobPage;
import ch.exmachina.cosmo42.entities.IngestionJobPageStatus;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.mappers.IngestionJobMapper;
import ch.exmachina.cosmo42.repositories.IngestionJobPageRepository;
import ch.exmachina.cosmo42.repositories.IngestionJobRepository;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionJobServiceTest {

    @Mock
    IngestionJobRepository ingestionJobRepository;
    @Mock
    IngestionJobPageRepository ingestionJobPageRepository;
    @Mock
    IngestionJobMapper ingestionJobMapper;

    IngestionJobService service;

    @BeforeEach
    void setUp() {
        service = new IngestionJobService(
                ingestionJobRepository,
                ingestionJobPageRepository,
                ingestionJobMapper
        );
    }

    @Test
    void createJob_setsAllFieldsWithPendingStatus() {
        IngestionJob expectedJob = new IngestionJob();
        expectedJob.setStatus(IngestionJobStatus.PENDING);
        expectedJob.setOriginalFileName("report.pdf");
        expectedJob.setFileSizeBytes(2048L);
        expectedJob.setStoredFileUuid("file-uuid");
        when(ingestionJobMapper.toEntity("report.pdf", 2048L, "file-uuid")).thenReturn(expectedJob);
        when(ingestionJobRepository.save(expectedJob)).thenReturn(expectedJob);

        IngestionJob result = service.createJob("report.pdf", 2048L, "file-uuid");

        assertThat(result.getStatus()).isEqualTo(IngestionJobStatus.PENDING);
        assertThat(result.getOriginalFileName()).isEqualTo("report.pdf");
        assertThat(result.getFileSizeBytes()).isEqualTo(2048L);
        assertThat(result.getStoredFileUuid()).isEqualTo("file-uuid");
    }

    @Test
    void markFailed_truncatesErrorMessageAt2000Chars() {
        String longError = "e".repeat(3000);
        IngestionJob job = new IngestionJob();
        when(ingestionJobRepository.findByUuid("job-1")).thenReturn(Optional.of(job));

        service.markFailed("job-1", longError);

        assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("e".repeat(2000));
        verify(ingestionJobRepository).save(job);
    }

    @Test
    void markFailed_handlesNullErrorMessage() {
        IngestionJob job = new IngestionJob();
        when(ingestionJobRepository.findByUuid("job-1")).thenReturn(Optional.of(job));

        service.markFailed("job-1", null);

        assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
        assertThat(job.getErrorMessage()).isNull();
        verify(ingestionJobRepository).save(job);
    }

    @Test
    void markProcessing_setsStatusAndStartedAt() {
        IngestionJob job = new IngestionJob();
        when(ingestionJobRepository.findByUuid("job-1")).thenReturn(Optional.of(job));

        service.markProcessing("job-1");

        assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.PROCESSING);
        assertThat(job.getStartedAt()).isNotNull();
        verify(ingestionJobRepository).save(job);
    }

    @Test
    void markCompleted_setsStatusAndCompletedAt() {
        IngestionJob job = new IngestionJob();
        when(ingestionJobRepository.findByUuid("job-1")).thenReturn(Optional.of(job));

        service.markCompleted("job-1");

        assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.COMPLETED);
        assertThat(job.getCompletedAt()).isNotNull();
        verify(ingestionJobRepository).save(job);
    }

    @Test
    void markInterrupted_setsStatus() {
        IngestionJob job = new IngestionJob();
        when(ingestionJobRepository.findByUuid("job-1")).thenReturn(Optional.of(job));

        service.markInterrupted("job-1");

        assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.INTERRUPTED);
        verify(ingestionJobRepository).save(job);
    }

    @Test
    void markChunksEmbedded_setsFlag() {
        IngestionJob job = new IngestionJob();
        when(ingestionJobRepository.findByUuid("job-1")).thenReturn(Optional.of(job));

        service.markChunksEmbedded("job-1");

        assertThat(job.getChunksEmbedded()).isTrue();
        verify(ingestionJobRepository).save(job);
    }

    @Test
    void setKbDocumentUuid_setsField() {
        IngestionJob job = new IngestionJob();
        when(ingestionJobRepository.findByUuid("job-1")).thenReturn(Optional.of(job));

        service.setKbDocumentUuid("job-1", "doc-uuid");

        assertThat(job.getKbDocumentUuid()).isEqualTo("doc-uuid");
        verify(ingestionJobRepository).save(job);
    }

    @Test
    void setTotalPages_createsAllPageRecords() {
        IngestionJob job = new IngestionJob();
        when(ingestionJobRepository.findByUuid("uuid-1")).thenReturn(Optional.of(job));
        when(ingestionJobPageRepository.existsByJobAndPageIndex(any(), anyInt())).thenReturn(false);

        service.setTotalPages("uuid-1", 3);

        assertThat(job.getTotalPages()).isEqualTo(3);
        verify(ingestionJobRepository).save(job);
        verify(ingestionJobPageRepository, times(3)).save(any(IngestionJobPage.class));
    }

    @Test
    void setTotalPages_skipsAlreadyExistingPages() {
        IngestionJob job = new IngestionJob();
        when(ingestionJobRepository.findByUuid("uuid-1")).thenReturn(Optional.of(job));
        when(ingestionJobPageRepository.existsByJobAndPageIndex(job, 0)).thenReturn(true);
        when(ingestionJobPageRepository.existsByJobAndPageIndex(job, 1)).thenReturn(false);

        service.setTotalPages("uuid-1", 2);

        verify(ingestionJobPageRepository, times(1)).save(any(IngestionJobPage.class));
    }

    @Test
    void savePageResult_nullPage_marksPageFailedAndIncrementsAttempt() {
        IngestionJobPage page = new IngestionJobPage();
        page.setPageIndex(0);
        page.setAttemptCount(0);
        when(ingestionJobPageRepository.findByJob_UuidAndPageIndex("job-1", 0)).thenReturn(Optional.of(page));

        service.savePageResult("job-1", 0, null);

        assertThat(page.getStatus()).isEqualTo(IngestionJobPageStatus.FAILED);
        assertThat(page.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void savePageResult_validPage_marksPageCompletedAndIncrementsAttempt() {
        IngestionJobPage page = new IngestionJobPage();
        page.setPageIndex(0);
        page.setAttemptCount(0);
        DocumentPage documentPage = new DocumentPage(List.of());
        when(ingestionJobPageRepository.findByJob_UuidAndPageIndex("job-1", 0)).thenReturn(Optional.of(page));
        when(ingestionJobMapper.toChunksJson(documentPage)).thenReturn("{\"chunks\":[]}");

        service.savePageResult("job-1", 0, documentPage);

        assertThat(page.getStatus()).isEqualTo(IngestionJobPageStatus.COMPLETED);
        assertThat(page.getChunksJson()).isEqualTo("{\"chunks\":[]}");
        assertThat(page.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void savePageResult_pageNotFound_noop() {
        when(ingestionJobPageRepository.findByJob_UuidAndPageIndex("job-1", 7)).thenReturn(Optional.empty());

        service.savePageResult("job-1", 7, null);

        verify(ingestionJobPageRepository, never()).save(any());
    }

    @Test
    void loadCompletedPages_returnsOnlyCompletedPages() {
        IngestionJob job = new IngestionJob();
        IngestionJobPage completed = pageWith(IngestionJobPageStatus.COMPLETED, "{\"chunks\":[]}");
        IngestionJobPage failed = pageWith(IngestionJobPageStatus.FAILED, null);
        IngestionJobPage pending = pageWith(IngestionJobPageStatus.PENDING, null);
        DocumentPage documentPage = new DocumentPage(List.of());
        when(ingestionJobPageRepository.findByJobOrderByPageIndexAsc(job))
                .thenReturn(List.of(completed, failed, pending));
        when(ingestionJobMapper.toDocumentPage(completed)).thenReturn(documentPage);

        List<DocumentPage> result = service.loadCompletedPages(job);

        assertThat(result).hasSize(1);
    }

    @Test
    void loadCompletedPages_filtersOutPagesWithJsonError() {
        IngestionJob job = new IngestionJob();
        IngestionJobPage page = pageWith(IngestionJobPageStatus.COMPLETED, "invalid-json");
        when(ingestionJobPageRepository.findByJobOrderByPageIndexAsc(job)).thenReturn(List.of(page));
        when(ingestionJobMapper.toDocumentPage(page)).thenReturn(null);

        List<DocumentPage> result = service.loadCompletedPages(job);

        assertThat(result).isEmpty();
    }

    private IngestionJobPage pageWith(IngestionJobPageStatus status, String chunksJson) {
        IngestionJobPage page = new IngestionJobPage();
        page.setStatus(status);
        page.setChunksJson(chunksJson);
        return page;
    }
}
