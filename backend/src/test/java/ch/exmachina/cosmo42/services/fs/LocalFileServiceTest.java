package ch.exmachina.cosmo42.services.fs;

import ch.exmachina.cosmo42.exceptions.KBDocumentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalFileServiceTest {

    @TempDir
    Path storageRoot;

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

    @Test
    void saveCreatesDirectoryAutomatically() throws IOException {
        Path nestedPath = storageRoot.resolve("new").resolve("dir");
        ReflectionTestUtils.setField(service, "storagePath", nestedPath.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", new byte[]{1});

        FileReference ref = service.save(file);

        assertThat(Files.exists(nestedPath)).isTrue();
        assertThat(Files.exists(nestedPath.resolve(ref.getUuid()))).isTrue();
    }

    @Test
    void savePropagatesIOExceptionWhenTransferToFails() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("x.pdf");
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        doThrow(new IOException("disk full")).when(file).transferTo(any(Path.class));

        assertThatThrownBy(() -> service.save(file))
                .isInstanceOf(IOException.class)
                .hasMessage("disk full");
    }

    @Test
    void savePropagatesIOExceptionWhenCreateDirectoriesFails() throws IOException {
        Path blocked = storageRoot.resolve("blocker.txt");
        Files.write(blocked, "block".getBytes());
        Path blockedStorage = blocked.resolve("subdir");
        ReflectionTestUtils.setField(service, "storagePath", blockedStorage.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.save(file))
                .isInstanceOf(IOException.class);
    }

    @Test
    void loadPropagatesIOExceptionWhenReadAllBytesFails() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", new byte[]{1});
        FileReference ref = service.save(file);

        Files.delete(storageRoot.resolve(ref.getUuid()));
        Files.createDirectory(storageRoot.resolve(ref.getUuid()));

        assertThatThrownBy(() -> service.load(ref.getUuid()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void deletePropagatesIOExceptionWhenFileDeleteFails() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.pdf", "application/pdf", new byte[]{1});
        FileReference ref = service.save(file);

        Files.delete(storageRoot.resolve(ref.getUuid()));
        Files.createDirectory(storageRoot.resolve(ref.getUuid()));
        Files.createFile(storageRoot.resolve(ref.getUuid()).resolve("nested.txt"));

        assertThatThrownBy(() -> service.delete(ref.getUuid()))
                .isInstanceOf(IOException.class);
    }
}
