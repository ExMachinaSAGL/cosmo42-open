package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.dto.*;
import ch.exmachina.cosmo42.mappers.ChatConversationMapper;
import ch.exmachina.cosmo42.services.chat.ChatConversationService;
import ch.exmachina.cosmo42.services.chat.ChatConversationWithMessages;
import ch.exmachina.cosmo42.services.chat.ChatService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class ChatController {

    ChatService chatService;
    ChatConversationService conversationService;
    ChatConversationMapper conversationMapper;

    @GetMapping
    public Page<ChatConversationListItemDTO> list(@PageableDefault(size = 20) Pageable pageable) {
        return conversationService.list(pageable).map(conversationMapper::toListItem);
    }

    @GetMapping("/{uuid}")
    public ChatConversationDTO get(@PathVariable String uuid) {
        ChatConversationWithMessages bundle = conversationService.get(uuid);
        return conversationMapper.toDetail(bundle.conversation(), bundle.messages());
    }

    @PatchMapping("/{uuid}")
    public ChatConversationListItemDTO rename(
            @PathVariable String uuid,
            @Valid @RequestBody ChatTitleUpdateDTO body
    ) {
        return conversationMapper.toListItem(conversationService.rename(uuid, body.title()));
    }

    @PostMapping("/{uuid}/title:regenerate")
    public ChatConversationListItemDTO regenerate(@PathVariable String uuid) {
        return conversationMapper.toListItem(conversationService.regenerateTitle(uuid));
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String uuid) {
        conversationService.delete(uuid);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatResponseDTO>> chatStream(@Valid @RequestBody ChatRequestDTO chatRequestDTO) {
        return chatService.processChat(chatRequestDTO);
    }

}
