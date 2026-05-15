package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.exceptions.GlobalExceptionHandler;
import ch.exmachina.cosmo42.mappers.ChatConversationMapper;
import ch.exmachina.cosmo42.services.chat.ChatConversationService;
import ch.exmachina.cosmo42.services.chat.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class)
@Import(GlobalExceptionHandler.class)
class ChatControllerValidationTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ChatService chatService;
    @MockitoBean ChatConversationService conversationService;
    @MockitoBean ChatConversationMapper conversationMapper;

    @Test
    void emptyMessageReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uuid\":null,\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.message").exists());
    }

    @Test
    void whitespaceMessageReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uuid\":null,\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
