package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.dto.DocumentDTO;
import ch.exmachina.cosmo42.dto.DownloadDocumentDTO;
import ch.exmachina.cosmo42.exceptions.GlobalExceptionHandler;
import ch.exmachina.cosmo42.exceptions.KBDocumentNotFoundException;
import ch.exmachina.cosmo42.services.KBDocumentService;
import ch.exmachina.cosmo42.services.MimeTypeService;
import ch.exmachina.cosmo42.testsupport.FileFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = KBDocumentsController.class)
@Import(GlobalExceptionHandler.class)
class KBDocumentsControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    KBDocumentService kbDocumentService;
    @MockitoBean
    MimeTypeService mimeTypeService;

    @Test
    void getListReturnsAllDocuments() throws Exception {
        when(kbDocumentService.listAllKBDocuments()).thenReturn(List.of(
                DocumentDTO.builder().fileUuid("file-1").fileName("a.pdf").status("loaded").build(),
                DocumentDTO.builder().fileUuid("file-2").fileName("b.pdf").status("loading").build()));

        mockMvc.perform(get("/api/v1/kb/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileUuid").value("file-1"))
                .andExpect(jsonPath("$[0].fileName").value("a.pdf"))
                .andExpect(jsonPath("$[1].fileUuid").value("file-2"));
    }

    @Test
    void uploadValidPdfReturns202AndDelegatesToService() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", FileFixtures.singlePagePdf("hi"));
        when(mimeTypeService.isSupportedMimeType(any())).thenReturn(true);
        when(kbDocumentService.enqueueKBDocument(any())).thenReturn(
                DocumentDTO.builder().fileUuid("file-uuid").fileName("doc.pdf").status("loading").build());

        mockMvc.perform(multipart("/api/v1/kb/documents").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.fileUuid").value("file-uuid"))
                .andExpect(jsonPath("$.fileName").value("doc.pdf"))
                .andExpect(jsonPath("$.status").value("loading"));

        verify(kbDocumentService).enqueueKBDocument(any());
    }

    @Test
    void uploadValidDocxReturns202() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.docx", "application/octet-stream", FileFixtures.minimalDocx());
        when(mimeTypeService.isSupportedMimeType(any())).thenReturn(true);
        when(kbDocumentService.enqueueKBDocument(any())).thenReturn(
                DocumentDTO.builder().fileUuid("file-uuid").fileName("doc.docx").status("loading").build());

        mockMvc.perform(multipart("/api/v1/kb/documents").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.fileName").value("doc.docx"));

        verify(kbDocumentService).enqueueKBDocument(any());
    }

    @Test
    void uploadEmptyFileReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/v1/kb/documents").file(file))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(kbDocumentService);
    }

    @Test
    void uploadUnsupportedFileReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "just text".getBytes());
        when(mimeTypeService.isSupportedMimeType(any())).thenReturn(false);

        mockMvc.perform(multipart("/api/v1/kb/documents").file(file))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(kbDocumentService);
    }

    @Test
    void getDocumentReturns200WithBody() throws Exception {
        when(kbDocumentService.getDocument("file-uuid")).thenReturn(Optional.of(
                DocumentDTO.builder().fileUuid("file-uuid").fileName("doc.pdf").status("loaded").build()));

        mockMvc.perform(get("/api/v1/kb/documents/{uuid}", "file-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUuid").value("file-uuid"))
                .andExpect(jsonPath("$.status").value("loaded"));
    }

    @Test
    void getDocumentReturns404WhenMissing() throws Exception {
        when(kbDocumentService.getDocument("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/kb/documents/{uuid}", "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadReturnsBytesWithAttachmentHeader() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(kbDocumentService.downloadKBDocument(uuid.toString())).thenReturn(
                DownloadDocumentDTO.builder()
                        .fileUuid(uuid.toString())
                        .fileName("report.pdf")
                        .content(new byte[]{1, 2, 3, 4})
                        .build());

        mockMvc.perform(get("/api/v1/kb/documents/{uuid}/download", uuid))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"report.pdf\""))
                .andExpect(header().longValue("Content-Length", 4))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}));
    }

    @Test
    void downloadOnMissingDocumentReturns404() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(kbDocumentService.downloadKBDocument(eq(uuid.toString())))
                .thenThrow(new KBDocumentNotFoundException(uuid.toString()));

        mockMvc.perform(get("/api/v1/kb/documents/{uuid}/download", uuid))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadWithMalformedUuidReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/kb/documents/not-a-uuid/download"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(kbDocumentService);
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID uuid = UUID.randomUUID();
        doNothing().when(kbDocumentService).deleteKBDocument(uuid.toString());

        mockMvc.perform(delete("/api/v1/kb/documents/{uuid}", uuid))
                .andExpect(status().isNoContent());

        verify(kbDocumentService).deleteKBDocument(uuid.toString());
    }

    @Test
    void deleteWithMalformedUuidReturns400() throws Exception {
        mockMvc.perform(delete("/api/v1/kb/documents/not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(kbDocumentService);
    }
}
