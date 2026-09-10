package ch.exmachina.cosmo42.services.kb;

import static java.lang.Integer.parseInt;
import static java.util.Comparator.comparing;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;

import ch.exmachina.cosmo42.dto.CitationEntryDTO;
import ch.exmachina.cosmo42.services.chat.tools.KBDocumentSimilaritySearchTool.ChunkDTO;

/**
 * Collects citations while streaming the response and storing a message in {@link ChatMemory}.
 */
public class CitationCollector {

    private static final Pattern PATTERN = Pattern.compile("__CITE_(\\d+)__");

    private final SortedSet<Integer> citations = new TreeSet<>();

    private final List<ChunkDTO> chunks = new LinkedList<>();

    public void collectChunks(List<ChunkDTO> context) {
        chunks.addAll(context);
    }

    public void collectCitations(String text) {
        collectCitations(text, citations);
    }

    public List<CitationEntryDTO> assemble(AssistantMessage message) {
        var citations = new TreeSet<Integer>();
        collectCitations(message.getText(), citations);
        return assemble(citations, chunks);
    }

    public List<CitationEntryDTO> assemble() {
        return assemble(citations, chunks);
    }

    private static void collectCitations(String text, SortedSet<Integer> citations) {
        var matcher = PATTERN.matcher(text);
        while (matcher.find()) {
            citations.add(parseInt(matcher.group(1)));
        }
    }

    private static List<CitationEntryDTO> assemble(SortedSet<Integer> citations, List<ChunkDTO> chunks) {

        var sortedChunks = chunks.stream().sorted(comparing(ChunkDTO::id)).toList().listIterator();
        var sortedCitations = citations.iterator();
        var result = new ArrayList<CitationEntryDTO>(citations.size());
        for (var id = 1; sortedCitations.hasNext();) {
            var citation = sortedCitations.next();
            while (sortedChunks.hasNext()) {
                var chunk = sortedChunks.next();
                if (chunk.id() == citation) {
                    result.add(entry(chunk, id++));
                    break;
                } else if (chunk.id() > citation) {
                    // no matching citation
                    sortedChunks.previous();
                    break;
                }
            }
        }
        return result;
    }

    private static CitationEntryDTO entry(ChunkDTO chunk, int newId) {
        return new CitationEntryDTO(newId, chunk.id(), chunk.fileName(), chunk.uuid(), chunk.sourcePages());
    }
}
