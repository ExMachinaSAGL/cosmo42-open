package ch.exmachina.cosmo42.exceptions;

public class ChatConversationHasNoUserMessageException extends IllegalStateException {
    public ChatConversationHasNoUserMessageException(String message) {
        super(message);
    }
}
