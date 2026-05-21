package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.BaseTest;
import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.dto.DownloadDocumentDTO;
import ch.exmachina.cosmo42.entities.IngestionJob;
import ch.exmachina.cosmo42.entities.IngestionJobStatus;
import ch.exmachina.cosmo42.entities.KBDocument;
import ch.exmachina.cosmo42.exceptions.FileSaveException;
import ch.exmachina.cosmo42.exceptions.KBDocumentNotFoundException;
import ch.exmachina.cosmo42.mappers.KBDocumentMapper;
import ch.exmachina.cosmo42.repositories.IngestionJobRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentChunkRepository;
import ch.exmachina.cosmo42.repositories.KBDocumentRepository;
import ch.exmachina.cosmo42.services.fs.FileReference;
import ch.exmachina.cosmo42.services.fs.FileService;
import ch.exmachina.cosmo42.testsupport.Fixtures;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KBDocumentServiceTest extends BaseTest {

    @Mock KBDocumentRepository kbDocumentRepository;
    @Mock KBDocumentChunkRepository kbDocumentChunkRepository;
    @Mock IngestionJobRepository ingestionJobRepository;
    @Mock FileService fileService;
    @Mock KBDocumentMapper kbDocumentMapper;
    @Mock IngestionJobService ingestionJobService;
    @Mock KBDocumentIngestionProcessor ingestionProcessor;

    @InjectMocks KBDocumentService service;

    private MultipartFile pdfFile() {
        return new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    private IngestionJob job(String uuid, String storedFileUuid, IngestionJobStatus status) {
        IngestionJob j = new IngestionJob();
        j.setUuid(uuid);
        j.setStoredFileUuid(storedFileUuid);
        j.setOriginalFileName("doc.pdf");
        j.setFileSizeBytes(3L);
        j.setStatus(status);
        return j;
    }

    @Test
    void listAllKBDocuments_delegatesToIngestionRepoAndMaps() {
        IngestionJob j1 = job("job-1", "file-1", IngestionJobStatus.COMPLETED);
        j1.setId(1L);
        IngestionJob j2 = job("job-2", "file-2", IngestionJobStatus.PENDING);
        j2.setId(2L);
        DocumentDTO d1 = DocumentDTO.builder().fileUuid("file-1").fileName("doc.pdf").build();
        DocumentDTO d2 = DocumentDTO.builder().fileUuid("file-2").fileName("doc.pdf").build();
        when(ingestionJobRepository.findAll()).thenReturn(List.of(j1, j2));
        when(kbDocumentMapper.toDocumentDTO(j1)).thenReturn(d1);
        when(kbDocumentMapper.toDocumentDTO(j2)).thenReturn(d2);

        List<DocumentDTO> result = service.listAllKBDocuments();

        assertThat(result).extracting(DocumentDTO::getFileUuid).containsExactly("file-1", "file-2");
    }

    @Test
    void enqueueKBDocument_savesFileCreatesJobAndTriggersAsync() throws IOException {
        MultipartFile file = pdfFile();
        FileReference ref = FileReference.builder().uuid("file-uuid").fileName("doc.pdf").fileSize(3L).build();
        IngestionJob created = job("job-uuid", "file-uuid", IngestionJobStatus.PENDING);
        DocumentDTO dto = DocumentDTO.builder().fileUuid("file-uuid").fileName("doc.pdf")
                .status("loading").build();

        when(fileService.save(file)).thenReturn(ref);
        when(ingestionJobService.createJob("doc.pdf", 3L, "file-uuid")).thenReturn(created);
        when(kbDocumentMapper.toDocumentDTO(created)).thenReturn(dto);

        DocumentDTO result = service.enqueueKBDocument(file);

        assertThat(result.getFileUuid()).isEqualTo("file-uuid");
        assertThat(result.getStatus()).isEqualTo("loading");
        verify(ingestionProcessor).processAsync("job-uuid");
    }

    @Test
    void enqueueKBDocument_ioErrorOnFileSave_throwsFileSaveException() throws IOException {
        MultipartFile file = pdfFile();
        when(fileService.save(any())).thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> service.enqueueKBDocument(file))
                .isInstanceOf(FileSaveException.class);
        verify(ingestionJobService, never()).createJob(any(), anyLong(), any());
        verify(ingestionProcessor, never()).processAsync(any());
    }

    @Test
    void getDocument_returnsMappedDtoWhenJobExists() {
        IngestionJob existing = job("job-uuid", "file-uuid", IngestionJobStatus.PROCESSING);
        DocumentDTO dto = DocumentDTO.builder().fileUuid("file-uuid").fileName("doc.pdf")
                .status("loading").build();
        when(ingestionJobRepository.findByStoredFileUuid("file-uuid")).thenReturn(Optional.of(existing));
        when(kbDocumentMapper.toDocumentDTO(existing)).thenReturn(dto);

        Optional<DocumentDTO> result = service.getDocument("file-uuid");

        assertThat(result).isPresent();
        assertThat(result.get().getFileUuid()).isEqualTo("file-uuid");
        assertThat(result.get().getStatus()).isEqualTo("loading");
    }

    @Test
    void getDocument_returnsEmptyWhenMissing() {
        when(ingestionJobRepository.findByStoredFileUuid("missing")).thenReturn(Optional.empty());

        assertThat(service.getDocument("missing")).isEmpty();
    }

    @Test
    void downloadKBDocument_returnsContentAndMetadata() throws IOException {
        KBDocument doc = Fixtures.document("doc-uuid", "report.pdf");
        DownloadDocumentDTO mapped = DownloadDocumentDTO.builder()
                .fileUuid("doc-uuid").fileName("report.pdf").build();
        when(kbDocumentRepository.findByUuid("doc-uuid")).thenReturn(Optional.of(doc));
        when(kbDocumentMapper.toDownloadDocumentDTO(doc)).thenReturn(mapped);
        when(fileService.load("doc-uuid")).thenReturn(new byte[]{9, 8, 7});

        DownloadDocumentDTO result = service.downloadKBDocument("doc-uuid");

        assertThat(result.getFileUuid()).isEqualTo("doc-uuid");
        assertThat(result.getFileName()).isEqualTo("report.pdf");
        assertThat(result.getContent()).containsExactly(9, 8, 7);
    }

    @Test
    void downloadKBDocument_throwsNotFoundWhenMissing() {
        when(kbDocumentRepository.findByUuid("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadKBDocument("missing"))
                .isInstanceOf(KBDocumentNotFoundException.class);
    }

    @Test
    void downloadKBDocument_wrapsFileServiceIoException() throws IOException {
        KBDocument doc = Fixtures.document("doc-uuid", "report.pdf");
        when(kbDocumentRepository.findByUuid("doc-uuid")).thenReturn(Optional.of(doc));
        when(kbDocumentMapper.toDownloadDocumentDTO(doc))
                .thenReturn(DownloadDocumentDTO.builder().fileUuid("doc-uuid").fileName("report.pdf").build());
        when(fileService.load("doc-uuid")).thenThrow(new IOException("read failed"));

        assertThatThrownBy(() -> service.downloadKBDocument("doc-uuid"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error downloading file");
    }

    @Test
    void deleteKBDocument_removesJobsChunksDocumentAndFileInOrder() throws IOException {
        service.deleteKBDocument("doc-uuid");

        InOrder inOrder = inOrder(ingestionJobRepository, kbDocumentChunkRepository,
                kbDocumentRepository, fileService);
        inOrder.verify(ingestionJobRepository).deleteByKbDocumentUuid("doc-uuid");
        inOrder.verify(kbDocumentChunkRepository).deleteByKbDocument_Uuid("doc-uuid");
        inOrder.verify(kbDocumentRepository).deleteByUuid("doc-uuid");
        inOrder.verify(fileService).delete("doc-uuid");
    }

    @Test
    void deleteKBDocument_wrapsFileServiceIoException() throws IOException {
        doThrow(new IOException("perm denied")).when(fileService).delete("doc-uuid");

        assertThatThrownBy(() -> service.deleteKBDocument("doc-uuid"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error deleting file");
    }
}
