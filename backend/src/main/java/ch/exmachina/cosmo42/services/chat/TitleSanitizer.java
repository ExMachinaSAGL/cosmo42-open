package ch.exmachina.cosmo42.services.chat;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class TitleSanitizer {

    private static final int MAX_LENGTH = 80;
    private static final String ELLIPSIS = "…";
    private static final Set<Character> SURROUNDING_QUOTES = Set.of(
            '"', '\'', '`',
            '«', '»',
            '“', '”',
            '‘', '’'
    );
    private static final Pattern PREFIX_PATTERN = Pattern.compile(
            "^(title|titolo)\\s*:\\s*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    public Optional<String> sanitize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }

        String s = raw.trim();
        if (s.isEmpty()) {
            return Optional.empty();
        }

        int nl = s.indexOf('\n');
        if (nl >= 0) {
            s = s.substring(0, nl);
        }

        s = stripQuotes(s.trim());
        s = PREFIX_PATTERN.matcher(s).replaceFirst("").trim();
        s = stripQuotes(s);
        s = WHITESPACE_RUN.matcher(s).replaceAll(" ").trim();

        if (s.isEmpty()) {
            return Optional.empty();
        }

        if (s.length() > MAX_LENGTH) {
            int cutAt = MAX_LENGTH - ELLIPSIS.length();
            int lastSpace = s.lastIndexOf(' ', cutAt);
            int boundary = (lastSpace > MAX_LENGTH / 2) ? lastSpace : cutAt;
            s = s.substring(0, boundary).trim() + ELLIPSIS;
        }

        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }

    private static String stripQuotes(String s) {
        while (s.length() >= 2
                && SURROUNDING_QUOTES.contains(s.charAt(0))
                && SURROUNDING_QUOTES.contains(s.charAt(s.length() - 1))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }
}
