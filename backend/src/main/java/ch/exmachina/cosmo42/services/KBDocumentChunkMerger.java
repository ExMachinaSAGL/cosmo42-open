package ch.exmachina.cosmo42.services;

import static ch.exmachina.cosmo42.services.KBDocumentIngestionProcessor.joinTexts;
import static java.util.Comparator.comparing;
import static java.util.Objects.nonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import ch.exmachina.cosmo42.entities.KBDocumentChunk;
import ch.exmachina.cosmo42.services.kb.schema.Chunk;
import ch.exmachina.cosmo42.services.kb.schema.DocumentPage;

@Component
class KBDocumentChunkMerger {

	List<KBDocumentChunk> mergePages(List<Map.Entry<Integer, DocumentPage>> pages) {
		var orderedPages = pages.stream().filter(this::valid).sorted(comparing(Map.Entry::getKey)).toList();

		var merged = new LinkedList<KBDocumentChunk>();

		for (int i = 0; i < orderedPages.size(); i++) {
			var page = orderedPages.get(i);
			var source = page.getValue();
			List<Chunk> chunks = source.getChunks();
			if (chunks.isEmpty()) {
				continue;
			}
			chunks.stream().limit(chunks.size() - 1)
				.map(c -> createDocumentChunk(c, page.getKey(), page.getKey()))
				.forEach(merged::add);
			var lastChunk = chunks.getLast();
			var kbChunk = lastChunk.getContinuesOnNextPage()
					? joinContinuingChunks(lastChunk, i, orderedPages)
					: createDocumentChunk(lastChunk, page.getKey(), page.getKey());
			merged.add(kbChunk);
		}
		return merged;
	}

	private boolean valid(Map.Entry<Integer, DocumentPage> page) {
		return nonNull(page) && nonNull(page.getValue()) && nonNull(page.getValue().getChunks());
	}

	private KBDocumentChunk joinContinuingChunks(Chunk start, final int offset,
			List<Map.Entry<Integer, DocumentPage>> orderedPages) {
		var page = orderedPages.get(offset);
		var startPage = page;
		var lastPage = page;
		for(var i = offset + 1; start.getContinuesOnNextPage() && i < orderedPages.size(); i++) {
			lastPage = orderedPages.get(i);
			if (lastPage.getKey() == page.getKey() + 1) {
				var chunks = lastPage.getValue().getChunks();
				var continuationChunk = chunks.stream()
						.filter(chunk -> Objects.equals(start.getType(), chunk.getType()))
						.findFirst();
				if (continuationChunk.isPresent()) {
					var continuation = continuationChunk.get();
					start.setContent(joinTexts(start.getContent(), continuation.getContent()));
					start.setSummary(joinTexts(start.getSummary(), continuation.getSummary()));
					start.setContinuesOnNextPage(continuation.getContinuesOnNextPage());
					lastPage.getValue().setChunks(chunks.stream().filter(c -> c != continuation).toList());
					page = lastPage;
				} else {
					// The model may have hallucinated
					start.setContinuesOnNextPage(false);
					return createDocumentChunk(start, startPage.getKey(), page.getKey());
				}
			} else {
				return createDocumentChunk(start, startPage.getKey(), page.getKey());
			}
		}
		return createDocumentChunk(start, startPage.getKey(), lastPage.getKey());
	}

	private KBDocumentChunk createDocumentChunk(Chunk chunk, int startPage, int endPage) {
		KBDocumentChunk kbChunk = new KBDocumentChunk();
		kbChunk.setUuid(UUID.randomUUID().toString());
		kbChunk.setStartPage(startPage);
		kbChunk.setEndPage(endPage);
		kbChunk.setType(chunk.getType());
		kbChunk.setContent(chunk.getContent());
		kbChunk.setSummary(chunk.getSummary());
		return kbChunk;
	}
}
