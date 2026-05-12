package ch.exmachina.cosmo42.services.kb.schema;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {

    @JsonPropertyDescription("The type of the chunk: 'text', 'table', or 'image'")
    private String type;

    @JsonPropertyDescription("The extracted text, markdown table, or visual description")
    private String content;

    @JsonPropertyDescription("Required ONLY if type is 'table'. A brief summary of the table's content and purpose. Leave null for other types.")
    private String summary;

    @JsonPropertyDescription("Set to true ONLY if this text chunk is physically cut off at the very bottom of the page and clearly continues on the next page. Otherwise false.")
    private Boolean continuesOnNextPage;

}
