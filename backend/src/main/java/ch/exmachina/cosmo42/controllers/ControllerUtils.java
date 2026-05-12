package ch.exmachina.cosmo42.controllers;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public class ControllerUtils {

    public static boolean isPdfSignatureValid(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[4];
            if (in.read(header) != 4) {
                return false;
            }
            return header[0] == 0x25 &&
                    header[1] == 0x50 &&
                    header[2] == 0x44 &&
                    header[3] == 0x46;
        } catch (Exception e) {
            return false;
        }
    }

}
