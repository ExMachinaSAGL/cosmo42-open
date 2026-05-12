package ch.exmachina.cosmo42.services.kb.schema;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPage {

        @JsonPropertyDescription("The list of chunks extracted from the page")
        private List<Chunk> chunks;

}