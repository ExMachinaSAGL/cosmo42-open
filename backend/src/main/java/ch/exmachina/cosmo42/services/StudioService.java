package ch.exmachina.cosmo42.services;

import ch.exmachina.cosmo42.exceptions.InvalidAttachmentException;
import ch.exmachina.cosmo42.services.kb.FileConverter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;


@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class StudioService {

    ChatModel chatModel;
    OpenAiChatOptions.Builder studioOptionsBuilder;
    FileConverter fileConverter;

    public String elaborate(String prompt, List<MultipartFile> attachments) {
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultOptions(studioOptionsBuilder)
                .build();

        List<Media> attachedMedias = new LinkedList<>();
        if (attachments != null && !attachments.isEmpty()) {
            try {
                for (MultipartFile file : attachments) {
                    byte[] pdf = fileConverter.convertSupportedFileToPdfFromBytes(file.getBytes(), file.getOriginalFilename());
                    List<byte[]> pageImages = fileConverter.convertPdfToImages(pdf);
                    for (byte[] pageImage : pageImages) {
                        attachedMedias.add(new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(pageImage)));
                    }
                }
            } catch (IOException e) {
                throw new InvalidAttachmentException(e.getMessage());
            }
        }

        String fullContent = chatClient.prompt()
                .user(u -> u.media(attachedMedias.toArray(new Media[0])).text(prompt))
                .stream()
                .content()
                .collectList()
                .map(chunks -> String.join("", chunks))
                .block();
        if (fullContent == null || fullContent.isBlank()) {
            return "ERROR: Empty response from the LLM";
        }
        return fullContent;
    }



}
