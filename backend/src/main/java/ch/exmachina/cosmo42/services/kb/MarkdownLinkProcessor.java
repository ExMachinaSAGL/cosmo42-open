package ch.exmachina.cosmo42.services.kb;

import ch.exmachina.cosmo42.entities.KBDocument;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class MarkdownLinkProcessor {

    Pattern pattern = Pattern.compile("REF_FILE_[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", Pattern.MULTILINE);

    public String replaceFileReferenceLinks(String messageChunk, List<KBDocument> allKBDocument) {
        Set<String> allUuids = allKBDocument.stream().map(KBDocument::getUuid).collect(Collectors.toSet());
        final Matcher matcher = pattern.matcher(messageChunk);
        while (matcher.find()) {
            String fullMatch = matcher.group(0);
            String uuid = fullMatch.replace("REF_FILE_", "");
            if(allUuids.contains(uuid)) {
                messageChunk = messageChunk.replace(fullMatch, buildMarkdownLink(uuid));
            } else {
                messageChunk = messageChunk.replace(fullMatch, "");
            }
        }
        return messageChunk;
    }

    private String buildMarkdownLink(String uuid) {
        return MessageFormat.format("[&#128279;](/api/v1/kb/documents/{0}/download)", uuid);
    }


}