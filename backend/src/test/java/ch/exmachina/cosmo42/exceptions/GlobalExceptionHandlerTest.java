package ch.exmachina.cosmo42.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void chatConversationHasNoUserMessageMapsTo409() throws Exception {
        mockMvc.perform(get("/__test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"))
                .andExpect(jsonPath("$.message").value("bad state"));
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

        @GetMapping(value = "/conflict", produces = MediaType.APPLICATION_JSON_VALUE)
        public String conflict() {
            throw new ChatConversationHasNoUserMessageException("bad state");
        }
    }
}
