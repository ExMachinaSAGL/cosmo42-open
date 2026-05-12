package ch.exmachina.cosmo42.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class KBDocumentNotFoundException extends RuntimeException {

    public KBDocumentNotFoundException(String uuid) {
        super("Unknown KB Document with uuid " + uuid);
    }

}
