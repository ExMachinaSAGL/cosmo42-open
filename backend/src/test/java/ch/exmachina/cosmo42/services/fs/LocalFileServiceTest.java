package ch.exmachina.cosmo42.services.fs;

import ch.exmachina.cosmo42.exceptions.KBDocumentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileServiceTest {

    @TempDir Path storageRoot;

    LocalFileService service;

    @BeforeEach
    void setUp() {
        service = new LocalFileService();
        ReflectionTestUtils.setField(service, "storagePath", storageRoot.toString());
    }

    @Test
    void saveWritesFileUnderGeneratedUuidAndReturnsReference() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "hello".getBytes());

        FileReference ref = service.save(file);

        assertThat(ref.getUuid()).matches("^[0-9a-f-]{36}$");
        assertThat(ref.getFileName()).isEqualTo("report.pdf");
        assertThat(ref.getFileSize()).isEqualTo(5L);
        Path written = storageRoot.resolve(ref.getUuid());
        assertThat(Files.exists(written)).isTrue();
        assertThat(Files.readAllBytes(written)).containsExactly("hello".getBytes());
    }

    @Test
    void saveCreatesStorageDirectoryIfMissing() throws IOException {
        Path notYetCreated = storageRoot.resolve("nested").resolve("storage");
        ReflectionTestUtils.setField(service, "storagePath", notYetCreated.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", new byte[]{1, 2, 3});

        FileReference ref = service.save(file);

        assertThat(Files.exists(notYetCreated)).isTrue();
        assertThat(Files.exists(notYetCreated.resolve(ref.getUuid()))).isTrue();
    }

    @Test
    void saveReturnsDistinctUuidsAcrossInvocations() throws IOException {
        MockMultipartFile a = new MockMultipartFile(
                "file", "a.pdf", "application/pdf", new byte[]{1});
        MockMultipartFile b = new MockMultipartFile(
                "file", "b.pdf", "application/pdf", new byte[]{2});

        FileReference refA = service.save(a);
        FileReference refB = service.save(b);

        assertThat(refA.getUuid()).isNotEqualTo(refB.getUuid());
    }

    @Test
    void loadReturnsBytesForSavedFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", new byte[]{42, 43, 44});
        FileReference ref = service.save(file);

        byte[] loaded = service.load(ref.getUuid());

        assertThat(loaded).containsExactly(42, 43, 44);
    }

    @Test
    void loadThrowsKBDocumentNotFoundExceptionWhenMissing() {
        assertThatThrownBy(() -> service.load("does-not-exist"))
                .isInstanceOf(KBDocumentNotFoundException.class);
    }

    @Test
    void deleteRemovesFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", new byte[]{1});
        FileReference ref = service.save(file);

        service.delete(ref.getUuid());

        assertThat(Files.exists(storageRoot.resolve(ref.getUuid()))).isFalse();
    }

    @Test
    void deleteThrowsKBDocumentNotFoundExceptionWhenMissing() {
        assertThatThrownBy(() -> service.delete("does-not-exist"))
                .isInstanceOf(KBDocumentNotFoundException.class);
    }

    @Test
    void saveLoadDeleteRoundTrip() throws IOException {
        byte[] payload = "round-trip payload".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", payload);

        FileReference ref = service.save(file);
        byte[] loaded = service.load(ref.getUuid());
        service.delete(ref.getUuid());

        assertThat(loaded).containsExactly(payload);
        assertThatThrownBy(() -> service.load(ref.getUuid()))
                .isInstanceOf(KBDocumentNotFoundException.class);
    }
}
