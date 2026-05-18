package ch.exmachina.cosmo42.exceptions;

public class InvalidChatTitleException extends IllegalArgumentException {
    public InvalidChatTitleException(String message) {
        super(message);
    }
}
