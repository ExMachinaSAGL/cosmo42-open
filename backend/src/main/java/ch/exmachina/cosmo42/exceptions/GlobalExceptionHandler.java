package ch.exmachina.cosmo42.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ChatConversationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ChatConversationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body("not_found", ex.getMessage()));
    }

    @ExceptionHandler(InvalidChatTitleException.class)
    public ResponseEntity<Map<String, Object>> handleBadArg(InvalidChatTitleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body("bad_request", ex.getMessage()));
    }

    @ExceptionHandler(ChatConversationHasNoUserMessageException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ChatConversationHasNoUserMessageException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("conflict", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(),
                        (a, b) -> a));
        Map<String, Object> resp = body("validation_failed", "Request validation failed");
        resp.put("fields", fieldErrors);
        return ResponseEntity.badRequest().body(resp);
    }

    private static Map<String, Object> body(String error, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", error);
        m.put("message", message);
        return m;
    }
}
