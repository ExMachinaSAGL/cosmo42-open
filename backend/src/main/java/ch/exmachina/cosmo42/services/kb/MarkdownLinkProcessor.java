package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.entities.KBDocument;

import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Pattern;

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
        String refVariant1 = "REF_FILE_"+kbDocument.getUuid();
        String refVariant2 = "REF_FILE_"+kbDocument.getFileName();
        String refVariant3 = "REF_FILE_"+kbDocument.getFileName().replace(" ", "_");
        String refVariant4 = "REF_FILE_"+kbDocument.getFileName().replace(" ", "_").replace(".", "_");
        String refVariant5 = MessageFormat.format("(?<=[^\\[]){0}(?=[^\\]])", Pattern.quote(kbDocument.getFileName()));
        result = result.replace(refVariant1, targetLink);
        result = result.replace(refVariant2, targetLink);
        result = result.replace(refVariant3, targetLink);
        result = result.replace(refVariant4, targetLink);
        result = result.replaceAll(refVariant5, targetLink);
        return result;
    }

    private String buildMarkdownLink(String refName, String uuid){
        return MessageFormat.format("[{0}](/api/v1/kb/documents/{1}/download)", refName, uuid);
    }


}