package ch.exmachina.cosmo42.exceptions;

public class ChatConversationNotFoundException extends RuntimeException {
    public ChatConversationNotFoundException(String uuid) {
        super("Chat conversation not found: " + uuid);
    }
}
