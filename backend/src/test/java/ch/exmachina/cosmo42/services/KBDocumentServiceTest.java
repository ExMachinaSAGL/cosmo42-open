package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.BaseTest;
import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.exceptions.FileSaveException;
import ch.exmachina.cosmo42.mappers.KBDocumentMapper;
import ch.exmachina.cosmo42.repositories.IngestionJobPageRepository;
import ch.exmachina.cosmo42.repositories.IngestionJobRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.fs.FileReference;
import ch.exmachina.cosmo42.services.fs.FileService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KBDocumentServiceTest extends BaseTest {

    @Mock KBDocumentRepository kbDocumentRepository;
    @Mock KBDocumentChunkRepository kbDocumentChunkRepository;
    @Mock IngestionJobRepository ingestionJobRepository;
    @Mock IngestionJobPageRepository ingestionJobPageRepository;
    @Mock FileService fileService;
    @Mock KBDocumentMapper kbDocumentMapper;
    @Mock IngestionJobService ingestionJobService;
    @Mock KBDocumentIngestionProcessor ingestionProcessor;

    @InjectMocks
    KBDocumentService service;

    @Test
    void enqueueKBDocument_savesFileCreatesJobAndTriggersAsync() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});
        FileReference ref = FileReference.builder().uuid("file-uuid").fileName("doc.pdf").fileSize(3L).build();
        IngestionJob job = new IngestionJob();
        job.setUuid("job-uuid");
        job.setStoredFileUuid("file-uuid");
        job.setOriginalFileName("doc.pdf");
        job.setFileSizeBytes(3L);
        job.setStatus(IngestionJobStatus.PENDING);
        DocumentDTO dto = DocumentDTO.builder().fileName("job-uuid").fileUuid("file-uuid").build();

        when(fileService.save(file)).thenReturn(ref);
        when(ingestionJobService.createJob("doc.pdf", 3L, "file-uuid")).thenReturn(job);
        when(kbDocumentMapper.toDocumentDTO(job)).thenReturn(dto);

        DocumentDTO result = service.enqueueKBDocument(file);

        assertThat(result.getFileUuid()).isEqualTo("file-uuid");
        verify(ingestionProcessor).processAsync("job-uuid");
    }

    @Test
    void enqueueKBDocument_ioErrorOnFileSave_throwsFileSaveException() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1});
        when(fileService.save(any())).thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> service.enqueueKBDocument(file))
                .isInstanceOf(FileSaveException.class);
        verify(ingestionJobService, never()).createJob(any(), anyLong(), any());
        verify(ingestionProcessor, never()).processAsync(any());
    }

    @Test
    void deleteKBDocument_removesJobsChunksDocumentAndFile() throws IOException {
        service.deleteKBDocument("doc-uuid");

        verify(ingestionJobRepository).deleteByKbDocumentUuid("doc-uuid");
        verify(ingestionJobPageRepository).deleteByJob_kbDocumentUuid("doc-uuid");
        verify(kbDocumentChunkRepository).deleteByKbDocument_Uuid("doc-uuid");
        verify(kbDocumentRepository).deleteByUuid("doc-uuid");
        verify(fileService).delete("doc-uuid");
    }

    @Test
    void deleteKBDocument_fileServiceIOError_throwsRuntimeException() throws IOException {
        doThrow(new IOException("perm denied")).when(fileService).delete(eq("doc-uuid"));

        assertThatThrownBy(() -> service.deleteKBDocument("doc-uuid"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("deleting");
    }
}
