package ch.exmachina.cosmo42.services.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TitleSanitizerTest {

    private final TitleSanitizer sanitizer = new TitleSanitizer();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t\n", "\n\n\n" })
    void returnsEmptyOnBlankInput(String input) {
        assertThat(sanitizer.sanitize(input)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("sanitizationCases")
    void sanitizesRawLlmOutput(String input, String expected) {
        assertThat(sanitizer.sanitize(input)).contains(expected);
    }

    static Stream<Arguments> sanitizationCases() {
        return Stream.of(
                Arguments.of("Simple Title", "Simple Title"),
                Arguments.of("  padded  ", "padded"),
                Arguments.of("\"Quoted Title\"", "Quoted Title"),
                Arguments.of("'single quoted'", "single quoted"),
                Arguments.of("`backtick`", "backtick"),
                Arguments.of("«smart quoted»", "smart quoted"),
                Arguments.of("“fancy quoted”", "fancy quoted"),
                Arguments.of("Title: My Chat", "My Chat"),
                Arguments.of("titolo: Il mio chat", "Il mio chat"),
                Arguments.of("TITLE: HELLO", "HELLO"),
                Arguments.of("First line\nsecond line", "First line"),
                Arguments.of("First line\r\nsecond", "First line"),
                Arguments.of("Title:  My   Chat   Here", "My Chat Here"),
                Arguments.of("Title: \"Quoted\"", "Quoted"),
                Arguments.of("emoji 🎉 title", "emoji 🎉 title")
        );
    }

    @Test
    void truncatesOnWordBoundaryWithEllipsis() {
        String longInput = "This is an extremely long generated title that the LLM unfortunately produced and we now have to chop";

        var result = sanitizer.sanitize(longInput);

        assertThat(result).isPresent();
        assertThat(result.get()).hasSizeLessThanOrEqualTo(80);
        assertThat(result.get()).endsWith("…");
        assertThat(result.get()).doesNotContain(" …");
    }

    @Test
    void shortInputUnchanged() {
        assertThat(sanitizer.sanitize("Hi")).contains("Hi");
    }

    @Test
    void blankAfterSanitizationReturnsEmpty() {
        assertThat(sanitizer.sanitize("\"\"")).isEmpty();
        assertThat(sanitizer.sanitize("Title:   ")).isEmpty();
    }
}
