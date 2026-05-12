package ch.exmachina.cosmo42.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class FileSaveException extends RuntimeException {

    public FileSaveException() {
        super("Impossible to save the file");
    }

}
