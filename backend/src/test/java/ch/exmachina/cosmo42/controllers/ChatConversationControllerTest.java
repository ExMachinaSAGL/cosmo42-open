package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.entities.ChatConversation;
import ch.exmachina.cosmo42.exceptions.ChatConversationNotFoundException;
import ch.exmachina.cosmo42.exceptions.GlobalExceptionHandler;
import ch.exmachina.cosmo42.mappers.ChatConversationMapper;
import ch.exmachina.cosmo42.services.chat.ChatConversationService;
import ch.exmachina.cosmo42.services.chat.ChatService;
import ch.exmachina.cosmo42.services.chat.ChatConversationWithMessages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ChatController.class)
@Import({GlobalExceptionHandler.class, ChatConversationMapper.class})
class ChatConversationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ChatConversationService service;
    @MockitoBean ChatService chatService;

    private ChatConversation conv(String uuid, String title) {
        ChatConversation c = new ChatConversation();
        c.setUuid(uuid);
        c.setTitle(title);
        c.setCreatedAt(LocalDateTime.parse("2026-05-15T12:00:00"));
        c.setUpdatedAt(LocalDateTime.parse("2026-05-15T12:00:00"));
        return c;
    }

    @Test
    void getListReturnsPage() throws Exception {
        when(service.list(any())).thenReturn(new PageImpl<>(List.of(conv("u-1", "First"))));

        mockMvc.perform(get("/api/v1/chat?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].uuid").value("u-1"))
                .andExpect(jsonPath("$.content[0].title").value("First"));
    }

    @Test
    void getOneReturnsDetail() throws Exception {
        when(service.get("u-1")).thenReturn(new ChatConversationWithMessages(
                conv("u-1", "First"), List.of()));

        mockMvc.perform(get("/api/v1/chat/u-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value("u-1"))
                .andExpect(jsonPath("$.title").value("First"));
    }

    @Test
    void getOneReturns404WhenUnknown() throws Exception {
        when(service.get("missing")).thenThrow(new ChatConversationNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/chat/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchRenamesTitle() throws Exception {
        when(service.rename(eq("u-1"), eq("New"))).thenReturn(conv("u-1", "New"));

        mockMvc.perform(patch("/api/v1/chat/u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New"));
    }

    @Test
    void patchValidationFailsOnBlankTitle() throws Exception {
        mockMvc.perform(patch("/api/v1/chat/u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").exists());

        verifyNoInteractions(service);
    }

    @Test
    void patchValidationFailsOnTooLongTitle() throws Exception {
        String tooLong = "x".repeat(81);
        mockMvc.perform(patch("/api/v1/chat/u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postRegenerateReturnsUpdated() throws Exception {
        when(service.regenerateTitle("u-1")).thenReturn(conv("u-1", "Regenerated"));

        mockMvc.perform(post("/api/v1/chat/u-1/title:regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Regenerated"));
    }

    @Test
    void postRegenerateReturns409WhenNoUserMessage() throws Exception {
        when(service.regenerateTitle("u-1")).thenThrow(new IllegalStateException("no user msg"));

        mockMvc.perform(post("/api/v1/chat/u-1/title:regenerate"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        doNothing().when(service).delete("u-1");

        mockMvc.perform(delete("/api/v1/chat/u-1"))
                .andExpect(status().isNoContent());

        verify(service).delete("u-1");
    }

    @Test
    void deleteReturns404WhenUnknown() throws Exception {
        doThrow(new ChatConversationNotFoundException("missing")).when(service).delete("missing");

        mockMvc.perform(delete("/api/v1/chat/missing"))
                .andExpect(status().isNotFound());
    }
}
