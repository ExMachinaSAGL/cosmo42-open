package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.entities.KBDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownLinkProcessorTest {

    MarkdownLinkProcessor markdownLinkProcessor = new MarkdownLinkProcessor();

    List<KBDocument> allKBDocument;


    @Test
    public void replaceUuidReference_oneInstance() {

        allKBDocument = List.of(
                buildKBDocument("f9da77ff-9838-4c5f-898f-0e3e1232f255", "filename.doc")
        );

        String messageChunk = "bla bla REF_FILE_f9da77ff-9838-4c5f-898f-0e3e1232f255\n\nbla bla";
        String newMessageChunk = markdownLinkProcessor.replaceFileReferenceLinks(messageChunk, allKBDocument);

        String expectedMessageChunk = "bla bla [&#128279;](/api/v1/kb/documents/f9da77ff-9838-4c5f-898f-0e3e1232f255/download)\n\nbla bla";
        assertThat(newMessageChunk).isEqualTo(expectedMessageChunk);
    }

    @Test
    public void replaceUuidReference_zeroInstances() {

        allKBDocument = List.of(
                buildKBDocument("f9da77ff-9838-4c5f-898f-0e3e1232f255", "filename.doc")
        );

        String messageChunk = "bla bla REF_FILE_xxxxxxxx-9838-4c5f-898f-0e3e1232f255\n\nbla bla";
        String newMessageChunk = markdownLinkProcessor.replaceFileReferenceLinks(messageChunk, allKBDocument);

        String expectedMessageChunk = messageChunk;
        assertThat(newMessageChunk).isEqualTo(expectedMessageChunk);
    }

    @Test
    public void replaceUuidReference_multipleInstances() {

        allKBDocument = List.of(
                buildKBDocument("f9da77ff-9838-4c5f-898f-0e3e1232f255", "filename.doc")
        );

        String messageChunk = "bla bla REF_FILE_f9da77ff-9838-4c5f-898f-0e3e1232f255\n\n" +
                "bla bla REF_FILE_f9da77ff-9838-4c5f-898f-0e3e1232f255";
        String newMessageChunk = markdownLinkProcessor.replaceFileReferenceLinks(messageChunk, allKBDocument);

        String expectedMessageChunk = "bla bla [&#128279;](/api/v1/kb/documents/f9da77ff-9838-4c5f-898f-0e3e1232f255/download)\n\nbla bla [&#128279;](/api/v1/kb/documents/f9da77ff-9838-4c5f-898f-0e3e1232f255/download)";
        assertThat(newMessageChunk).isEqualTo(expectedMessageChunk);
    }

    @Test
    public void replaceUuidReference_differentInstances() {

        allKBDocument = List.of(
                buildKBDocument("f9da77ff-1111-4c5f-898f-0e3e1232f255", "filename.doc"),
                buildKBDocument("f9da77ff-2222-4c5f-898f-0e3e1232f255", "filename.doc")
        );

        String messageChunk = "bla bla REF_FILE_f9da77ff-1111-4c5f-898f-0e3e1232f255\n\n" +
                "bla bla REF_FILE_f9da77ff-2222-4c5f-898f-0e3e1232f255";
        String newMessageChunk = markdownLinkProcessor.replaceFileReferenceLinks(messageChunk, allKBDocument);

        String expectedMessageChunk = "bla bla [&#128279;](/api/v1/kb/documents/f9da77ff-1111-4c5f-898f-0e3e1232f255/download)\n\nbla bla [&#128279;](/api/v1/kb/documents/f9da77ff-2222-4c5f-898f-0e3e1232f255/download)";
        assertThat(newMessageChunk).isEqualTo(expectedMessageChunk);
    }


    private KBDocument buildKBDocument(String uuid, String fileName) {
        KBDocument kbDocument = new KBDocument();
        kbDocument.setUuid(uuid);
        kbDocument.setFileName(fileName);
        return kbDocument;
    }

}