package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.entities.KBDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        String expectedMessageChunk = "bla bla [filename.doc](/api/v1/kb/documents/f9da77ff-9838-4c5f-898f-0e3e1232f255/download)\n\nbla bla";
        assertEquals(expectedMessageChunk, newMessageChunk);
    }

    @Test
    public void replaceUuidReference_zeroInstances() {

        allKBDocument = List.of(
                buildKBDocument("f9da77ff-9838-4c5f-898f-0e3e1232f255", "filename.doc")
        );

        String messageChunk = "bla bla REF_FILE_xxxxxxxx-9838-4c5f-898f-0e3e1232f255\n\nbla bla";
        String newMessageChunk = markdownLinkProcessor.replaceFileReferenceLinks(messageChunk, allKBDocument);

        String expectedMessageChunk = messageChunk;
        assertEquals(expectedMessageChunk, newMessageChunk);
    }

    @Test
    public void replaceUuidReference_multipleInstances() {

        allKBDocument = List.of(
                buildKBDocument("f9da77ff-9838-4c5f-898f-0e3e1232f255", "filename.doc")
        );

        String messageChunk = "bla bla REF_FILE_f9da77ff-9838-4c5f-898f-0e3e1232f255\n\n" +
                "bla bla REF_FILE_f9da77ff-9838-4c5f-898f-0e3e1232f255";
        String newMessageChunk = markdownLinkProcessor.replaceFileReferenceLinks(messageChunk, allKBDocument);

        String expectedMessageChunk = "bla bla [filename.doc](/api/v1/kb/documents/f9da77ff-9838-4c5f-898f-0e3e1232f255/download)\n\nbla bla [filename.doc](/api/v1/kb/documents/f9da77ff-9838-4c5f-898f-0e3e1232f255/download)";
        assertEquals(expectedMessageChunk, newMessageChunk);
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

        String expectedMessageChunk = "bla bla [filename.doc](/api/v1/kb/documents/f9da77ff-1111-4c5f-898f-0e3e1232f255/download)\n\nbla bla [filename.doc](/api/v1/kb/documents/f9da77ff-2222-4c5f-898f-0e3e1232f255/download)";
        assertEquals(expectedMessageChunk, newMessageChunk);
    }

    @Test
    public void replaceSpacedVariants_differentPatterns() {

        allKBDocument = List.of(
                buildKBDocument("f9da77ff-1111-4c5f-898f-0e3e1232f255", "file name 1.doc")
        );

        String messageChunk = "bla bla REF_FILE_file name 1.doc\n" +
                "bla bla REF_FILE_file_name_1.doc\n" +
                "bla bla REF_FILE_file_name_1_doc\n" +
                "bla bla REF_FILE-file-name-1-doc";
        String newMessageChunk = markdownLinkProcessor.replaceFileReferenceLinks(messageChunk, allKBDocument);

        String expectedLink = "[file name 1.doc](/api/v1/kb/documents/f9da77ff-1111-4c5f-898f-0e3e1232f255/download)";
        String expectedMessageChunk = "bla bla "+expectedLink+"\nbla bla "+expectedLink+"\nbla bla "+expectedLink+"\nbla bla REF_FILE-file-name-1-doc";
        assertEquals(expectedMessageChunk, newMessageChunk);
    }

    @Test
    public void replaceSpacedVariants_DifferentCases() {

        allKBDocument = List.of(
                buildKBDocument("f9da77ff-1111-4c5f-898f-0e3e1232f255", "file name 1.doc")
        );

        String messageChunk = "bla bla REF_FILE_file name 1.doc\n" +
                "bla bla REF_FILE_FiLe_NaMe_1.Doc\n" +
                "bla bla ref_file_file_name_1_doc";
        String newMessageChunk = markdownLinkProcessor.replaceFileReferenceLinks(messageChunk, allKBDocument);

        String expectedLink = "[file name 1.doc](/api/v1/kb/documents/f9da77ff-1111-4c5f-898f-0e3e1232f255/download)";
        String expectedMessageChunk = "bla bla "+expectedLink+"\nbla bla "+expectedLink+"\nbla bla "+expectedLink;
        assertEquals(expectedMessageChunk, newMessageChunk);
    }


    private KBDocument buildKBDocument(String uuid, String fileName) {
        KBDocument kbDocument = new KBDocument();
        kbDocument.setUuid(uuid);
        kbDocument.setFileName(fileName);
        return kbDocument;
    }

}