package ch.exmachina.cosmo42.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void chatConversationNotFoundMapsTo404() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"))
                .andExpect(jsonPath("$.message").value("Chat conversation not found: abc"));
    }

    @Test
    void invalidChatTitleMapsTo400() throws Exception {
        mockMvc.perform(get("/__test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value("nope"));
    }

    @Test
    void validationFailureReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/__test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "message": ""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.title").isNotEmpty())
                .andExpect(jsonPath("$.fields.message").isNotEmpty());
    }

    @Test
    void validationHandlerFallsBackToInvalidWhenDefaultMessageIsNull() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "dto");
        bindingResult.addError(new FieldError("dto", "title", null));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        var fields = (java.util.Map<String, String>) response.getBody().get("fields");
        assertThat(fields).containsEntry("title", "invalid");
    }

    @Test
    void validationHandlerHandlesEmptyFieldErrors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "dto");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("error")).isEqualTo("validation_failed");
        @SuppressWarnings("unchecked")
        var fields = (java.util.Map<String, String>) response.getBody().get("fields");
        assertThat(fields).isEmpty();
    }

    @Test
    void validationHandlerMergeFunctionKeepsFirstOfDuplicates() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "dto");
        bindingResult.addError(new FieldError("dto", "title", "First error"));
        bindingResult.addError(new FieldError("dto", "title", "Second error"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidation(ex);

        @SuppressWarnings("unchecked")
        var fields = (java.util.Map<String, String>) response.getBody().get("fields");
        assertThat(fields).containsEntry("title", "First error");
    }

    @RestController
    @RequestMapping("/__test")
    static class TestController {
        @GetMapping(value = "/not-found", produces = MediaType.APPLICATION_JSON_VALUE)
        public String notFound() {
            throw new ChatConversationNotFoundException("abc");
        }

        @GetMapping(value = "/bad-request", produces = MediaType.APPLICATION_JSON_VALUE)
        public String bad() {
            throw new InvalidChatTitleException("nope");
        }

        @PostMapping(value = "/validation", produces = MediaType.APPLICATION_JSON_VALUE)
        public String validation(@Valid @RequestBody ValidationDto dto) {
            return "ok";
        }
    }

    record ValidationDto(@NotBlank String title, @Size(min = 1, max = 100) String message) {}
}
