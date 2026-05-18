package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.entities.KBDocument;

import java.text.MessageFormat;
import java.util.List;

public class MarkdownLinkProcessor{

    public String replaceFileReferenceLinks(String messageChunk, List<KBDocument> allKBDocument) {
        String newMessageChunk = messageChunk;
        for(KBDocument kbDocument : allKBDocument) {
            newMessageChunk = replaceReferenceLink(newMessageChunk, kbDocument);
        }
        return newMessageChunk;
    }

    private String replaceReferenceLink(String chunkContent, KBDocument kbDocument) {
        String result = chunkContent;
        String targetLink = buildMarkdownLink(kbDocument.getFileName(), kbDocument.getUuid());
        String refVariant1 = "(?i)REF_FILE_"+kbDocument.getUuid();
        String refVariant2 = "(?i)REF_FILE_"+kbDocument.getFileName();
        String refVariant3 = "(?i)REF_FILE_"+kbDocument.getFileName().replaceAll(" ", "_");
        String refVariant4 = "(?i)REF_FILE_"+kbDocument.getFileName().replaceAll(" ", "_").replaceAll(".", "_");
        result = result.replaceAll(refVariant1, targetLink);
        result = result.replaceAll(refVariant2, targetLink);
        result = result.replaceAll(refVariant3, targetLink);
        result = result.replaceAll(refVariant4, targetLink);
        return result;
    }

    private String buildMarkdownLink(String refName, String uuid){
        return MessageFormat.format("[{0}](/api/v1/kb/documents/{1}/download)", refName, uuid);
    }


}
