package ch.exmachina.cosmo42.testsupport;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class EmbeddingMocks {

    private EmbeddingMocks() {}

    public static EmbeddingModel returningFixed(float[] vector) {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(any(String.class))).thenReturn(vector.clone());
        when(model.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest req = invocation.getArgument(0);
            List<Embedding> embeddings = new ArrayList<>(req.getInstructions().size());
            for (int i = 0; i < req.getInstructions().size(); i++) {
                embeddings.add(new Embedding(vector.clone(), i));
            }
            return new EmbeddingResponse(embeddings);
        });
        return model;
    }

    public static EmbeddingModel returningZeros(int dim) {
        return returningFixed(new float[dim]);
    }
}
