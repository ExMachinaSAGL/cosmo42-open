package ch.exmachina.cosmo42.dto;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ChatResponseDtoContractTest {

    @Autowired
    JacksonTester<ChatResponseDTO> json;

    @Nested
    class Shape {

        @Test
        void uuidEventSerializesWithStringTypeAndUuidPayload() throws IOException {
            ChatResponseDTO dto = ChatResponseDTO.builder()
                    .type(ChatEventType.UUID)
                    .data("11111111-1111-1111-1111-111111111111")
                    .build();

            JsonNode node = readTree(dto);

            assertThat(node.get("type").asText()).isEqualTo("UUID");
            assertThat(node.get("data").asText()).isEqualTo("11111111-1111-1111-1111-111111111111");
            assertThat(node.size()).isEqualTo(2);
        }

        @Test
        void statusEventSerializesWithStatusTypeAndStringPayload() throws IOException {
            ChatResponseDTO dto = ChatResponseDTO.builder()
                    .type(ChatEventType.STATUS)
                    .data("Analyzing the request...")
                    .build();

            JsonNode node = readTree(dto);

            assertThat(node.get("type").asText()).isEqualTo("STATUS");
            assertThat(node.get("data").asText()).isEqualTo("Analyzing the request...");
        }

        @Test
        void titleEventSerializesWithTitleTypeAndStringPayload() throws IOException {
            ChatResponseDTO dto = ChatResponseDTO.builder()
                    .type(ChatEventType.TITLE)
                    .data("Deploy Question")
                    .build();

            JsonNode node = readTree(dto);

            assertThat(node.get("type").asText()).isEqualTo("TITLE");
            assertThat(node.get("data").asText()).isEqualTo("Deploy Question");
        }

        @Test
        void chunkEventSerializesWithChunkTypeAndStringPayload() throws IOException {
            ChatResponseDTO dto = ChatResponseDTO.builder()
                    .type(ChatEventType.CHUNK)
                    .data("token-fragment ")
                    .build();

            JsonNode node = readTree(dto);

            assertThat(node.get("type").asText()).isEqualTo("CHUNK");
            assertThat(node.get("data").asText()).isEqualTo("token-fragment ");
        }

        @Test
        void completedEventOmitsDataField() throws IOException {
            ChatResponseDTO dto = ChatResponseDTO.builder()
                    .type(ChatEventType.COMPLETED)
                    .build();

            JsonNode node = readTree(dto);

            assertThat(node.get("type").asText()).isEqualTo("COMPLETED");
            assertThat(node.has("data"))
                    .as("COMPLETED carries no payload; the data field must be omitted, not null")
                    .isFalse();
            assertThat(node.size()).isEqualTo(1);
        }

        @Test
        void errorEventSerializesWithErrorTypeAndMessagePayload() throws IOException {
            ChatResponseDTO dto = ChatResponseDTO.builder()
                    .type(ChatEventType.ERROR)
                    .data("LLM upstream down")
                    .build();

            JsonNode node = readTree(dto);

            assertThat(node.get("type").asText()).isEqualTo("ERROR");
            assertThat(node.get("data").asText()).isEqualTo("LLM upstream down");
        }
    }

    @Nested
    class Contract {

        @Test
        void nullDataIsAlwaysOmittedRegardlessOfEventType() throws IOException {
            for (ChatEventType type : ChatEventType.values()) {
                ChatResponseDTO dto = ChatResponseDTO.builder().type(type).build();

                JsonNode node = readTree(dto);

                assertThat(node.has("data"))
                        .as("data must be omitted (not serialized as null) for %s", type)
                        .isFalse();
            }
        }

        @Test
        void typeFieldIsSerializedAsStringNotOrdinal() throws IOException {
            ChatResponseDTO dto = ChatResponseDTO.builder()
                    .type(ChatEventType.UUID)
                    .build();

            JsonNode node = readTree(dto);

            assertThat(node.get("type").isTextual())
                    .as("type must be the enum name, not its ordinal — clients depend on the string")
                    .isTrue();
        }

        @Test
        void allDeclaredEventTypesAreSerializable() throws IOException {
            for (ChatEventType type : ChatEventType.values()) {
                ChatResponseDTO dto = ChatResponseDTO.builder().type(type).data("any").build();

                JsonNode node = readTree(dto);

                assertThat(node.get("type").asText()).isEqualTo(type.name());
            }
        }

        @Test
        void declaredEventTypesPinsTheContractAgainstAccidentalAddition() {
            assertThat(ChatEventType.values())
                    .containsExactlyInAnyOrder(
                            ChatEventType.UUID,
                            ChatEventType.TITLE,
                            ChatEventType.STATUS,
                            ChatEventType.CHUNK,
                            ChatEventType.COMPLETED,
                            ChatEventType.ERROR);
        }
    }

    private JsonNode readTree(ChatResponseDTO dto) throws IOException {
        String raw = json.write(dto).getJson();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
    }
}
