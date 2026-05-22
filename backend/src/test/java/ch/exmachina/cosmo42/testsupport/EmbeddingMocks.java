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

    private EmbeddingMocks() {
    }

    public static EmbeddingModel returningFixed(float[] vector) {
        EmbeddingModel model = mock(EmbeddingModel.class);
        stubWithFixedVector(model, vector);
        when(model.embed(any(String.class))).thenReturn(vector.clone());
        return model;
    }

    public static EmbeddingModel returningZeros(int dim) {
        return returningFixed(new float[dim]);
    }

    public static void stubWithFixedVector(EmbeddingModel model, float[] vector) {
        when(model.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest req = invocation.getArgument(0);
            List<Embedding> embeddings = new ArrayList<>(req.getInstructions().size());
            for (int i = 0; i < req.getInstructions().size(); i++) {
                embeddings.add(new Embedding(vector.clone(), i));
            }
            return new EmbeddingResponse(embeddings);
        });
    }

    public static void stubReturningZeros(EmbeddingModel model, int dim) {
        stubWithFixedVector(model, new float[dim]);
    }
}
