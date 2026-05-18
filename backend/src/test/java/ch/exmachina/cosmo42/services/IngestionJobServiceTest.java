package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.BaseTest;
import ch.exmachina.cosmo42.dto.JobStatusDTO;
import ch.exmachina.cosmo42.entities.*;
import ch.exmachina.cosmo42.mappers.IngestionJobMapper;
import ch.exmachina.cosmo42.repositories.IngestionJobPageRepository;
import ch.exmachina.cosmo42.repositories.IngestionJobRepository;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestionJobServiceTest extends BaseTest {

    @Mock
    IngestionJobRepository ingestionJobRepository;
    @Mock
    IngestionJobPageRepository ingestionJobPageRepository;
    @Mock
    ObjectMapper objectMapper;

    IngestionJobService service;

    @BeforeEach
    void setUp() {
        service = new IngestionJobService(
                ingestionJobRepository,
                ingestionJobPageRepository,
                objectMapper,
                new IngestionJobMapper()
        );
    }

    @Test
    void createJob_setsAllFieldsWithPendingStatus() {
        when(ingestionJobRepository.save(any(IngestionJob.class))).thenAnswer(inv -> inv.getArgument(0));

        IngestionJob result = service.createJob("report.pdf", 2048L, "file-uuid");

        assertThat(result.getStatus()).isEqualTo(IngestionJobStatus.PENDING);
        assertThat(result.getOriginalFileName()).isEqualTo("report.pdf");
        assertThat(result.getFileSizeBytes()).isEqualTo(2048L);
        assertThat(result.getStoredFileUuid()).isEqualTo("file-uuid");
        assertThat(result.getUuid()).isNotBlank();
        assertThat(result.getCreatedAt()).isNotNull();
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
    void toStatusDTO_pendingJob_progressZeroNoDocumentUuid() {
        IngestionJob job = jobWith(IngestionJobStatus.PENDING);

        JobStatusDTO dto = service.toStatusDTO(job);

        assertThat(dto.getProgressPercent()).isEqualTo(0);
        assertThat(dto.getDocumentUuid()).isNull();
        assertThat(dto.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void toStatusDTO_completedJob_progress100AndDocumentUuid() {
        IngestionJob job = jobWith(IngestionJobStatus.COMPLETED);
        job.setKbDocumentUuid("doc-uuid");

        JobStatusDTO dto = service.toStatusDTO(job);

        assertThat(dto.getProgressPercent()).isEqualTo(100);
        assertThat(dto.getDocumentUuid()).isEqualTo("doc-uuid");
    }

    @Test
    void toStatusDTO_failedJob_includesErrorMessage() {
        IngestionJob job = jobWith(IngestionJobStatus.FAILED);
        job.setErrorMessage("something went wrong");

        JobStatusDTO dto = service.toStatusDTO(job);

        assertThat(dto.getErrorMessage()).isEqualTo("something went wrong");
        assertThat(dto.getDocumentUuid()).isNull();
    }

    @Test
    void calculateProgress_processingWithPagesAndStoredFile() {
        IngestionJob job = jobWith(IngestionJobStatus.PROCESSING);
        job.setTotalPages(10);
        job.setStoredFileUuid("stored");
        when(ingestionJobPageRepository.countByJobAndStatus(job, IngestionJobPageStatus.COMPLETED))
                .thenReturn(5L);

        JobStatusDTO dto = service.toStatusDTO(job);

        // 10 (conversion done) + 5*60/10 (pages) + 10 (stored file) = 50
        assertThat(dto.getProgressPercent()).isEqualTo(50);
    }

    @Test
    void calculateProgress_processingWithNullTotalPages_returnsZero() {
        IngestionJob job = jobWith(IngestionJobStatus.PROCESSING);
        job.setTotalPages(null);

        JobStatusDTO dto = service.toStatusDTO(job);

        assertThat(dto.getProgressPercent()).isEqualTo(0);
    }

    @Test
    void calculateProgress_neverExceeds99WhileProcessing() {
        IngestionJob job = jobWith(IngestionJobStatus.PROCESSING);
        job.setTotalPages(1);
        job.setStoredFileUuid("stored");
        job.setKbDocumentUuid("doc");
        when(ingestionJobPageRepository.countByJobAndStatus(job, IngestionJobPageStatus.COMPLETED))
                .thenReturn(1L);

        JobStatusDTO dto = service.toStatusDTO(job);

        assertThat(dto.getProgressPercent()).isLessThanOrEqualTo(99);
    }

    @Test
    void savePageResult_nullPage_marksPageFailedAndIncrementsAttempt() {
        IngestionJob job = new IngestionJob();
        IngestionJobPage page = new IngestionJobPage();
        page.setPageIndex(0);
        page.setAttemptCount(0);
        when(ingestionJobPageRepository.findByJobAndPageIndex(job, 0)).thenReturn(Optional.of(page));

        service.savePageResult(job, 0, null);

        assertThat(page.getStatus()).isEqualTo(IngestionJobPageStatus.FAILED);
        assertThat(page.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void savePageResult_validPage_marksPageCompletedAndIncrementsAttempt() throws JacksonException {
        IngestionJob job = new IngestionJob();
        IngestionJobPage page = new IngestionJobPage();
        page.setPageIndex(0);
        page.setAttemptCount(0);
        DocumentPage documentPage = new DocumentPage(List.of());
        when(ingestionJobPageRepository.findByJobAndPageIndex(job, 0)).thenReturn(Optional.of(page));
        when(objectMapper.writeValueAsString(documentPage)).thenReturn("{\"chunks\":[]}");

        service.savePageResult(job, 0, documentPage);

        assertThat(page.getStatus()).isEqualTo(IngestionJobPageStatus.COMPLETED);
        assertThat(page.getChunksJson()).isEqualTo("{\"chunks\":[]}");
        assertThat(page.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void savePageResult_pageNotFound_noop() {
        IngestionJob job = new IngestionJob();
        job.setUuid("job-1");
        when(ingestionJobPageRepository.findByJobAndPageIndex(job, 7)).thenReturn(Optional.empty());

        service.savePageResult(job, 7, null);

        verify(ingestionJobPageRepository, never()).save(any());
    }

    @Test
    void loadCompletedPages_returnsOnlyCompletedPages() throws JacksonException {
        IngestionJob job = new IngestionJob();
        IngestionJobPage completed = pageWith(IngestionJobPageStatus.COMPLETED, "{\"chunks\":[]}");
        IngestionJobPage failed = pageWith(IngestionJobPageStatus.FAILED, null);
        IngestionJobPage pending = pageWith(IngestionJobPageStatus.PENDING, null);
        DocumentPage documentPage = new DocumentPage(List.of());
        when(ingestionJobPageRepository.findByJobOrderByPageIndexAsc(job))
                .thenReturn(List.of(completed, failed, pending));
        when(objectMapper.readValue(anyString(), eq(DocumentPage.class))).thenReturn(documentPage);

        List<DocumentPage> result = service.loadCompletedPages(job);

        assertThat(result).hasSize(1);
    }

    @Test
    void loadCompletedPages_filtersOutPagesWithJsonError() throws JacksonException {
        IngestionJob job = new IngestionJob();
        IngestionJobPage page = pageWith(IngestionJobPageStatus.COMPLETED, "invalid-json");
        when(ingestionJobPageRepository.findByJobOrderByPageIndexAsc(job)).thenReturn(List.of(page));
        when(objectMapper.readValue(anyString(), eq(DocumentPage.class)))
                .thenThrow(new tools.jackson.core.exc.StreamReadException("parse error"));

        List<DocumentPage> result = service.loadCompletedPages(job);

        assertThat(result).isEmpty();
    }

    // --- helpers ---

    private IngestionJob jobWith(IngestionJobStatus status) {
        IngestionJob job = new IngestionJob();
        job.setUuid("test-uuid");
        job.setStatus(status);
        return job;
    }

    private IngestionJobPage pageWith(IngestionJobPageStatus status, String chunksJson) {
        IngestionJobPage page = new IngestionJobPage();
        page.setStatus(status);
        page.setChunksJson(chunksJson);
        return page;
    }

}
