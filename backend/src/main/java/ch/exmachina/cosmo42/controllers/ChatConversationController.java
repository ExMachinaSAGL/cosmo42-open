package ch.exmachina.cosmo42.controllers;

import ch.exmachina.cosmo42.dto.ChatConversationDTO;
import ch.exmachina.cosmo42.dto.ChatConversationListItemDTO;
import ch.exmachina.cosmo42.dto.ChatTitleUpdateDTO;
import ch.exmachina.cosmo42.mappers.ChatConversationMapper;
import ch.exmachina.cosmo42.services.chat.ChatConversationService;
import ch.exmachina.cosmo42.services.chat.ChatConversationWithMessages;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatConversationController {

    ChatConversationService service;
    ChatConversationMapper mapper;

    @GetMapping
    public Page<ChatConversationListItemDTO> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable).map(mapper::toListItem);
    }

    @GetMapping("/{uuid}")
    public ChatConversationDTO get(@PathVariable String uuid) {
        ChatConversationWithMessages bundle = service.get(uuid);
        return mapper.toDetail(bundle.conversation(), bundle.messages());
    }

    @PatchMapping("/{uuid}")
    public ChatConversationListItemDTO rename(
            @PathVariable String uuid,
            @Valid @RequestBody ChatTitleUpdateDTO body
    ) {
        return mapper.toListItem(service.rename(uuid, body.title()));
    }

    @PostMapping("/{uuid}/title:regenerate")
    public ChatConversationListItemDTO regenerate(@PathVariable String uuid) {
        return mapper.toListItem(service.regenerateTitle(uuid));
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
