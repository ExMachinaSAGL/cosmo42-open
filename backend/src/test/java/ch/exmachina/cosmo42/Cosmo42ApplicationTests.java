package ch.exmachina.cosmo42;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class Cosmo42ApplicationTests {

    @Test
    void mainMethodIsPublicForJavaLauncher() throws NoSuchMethodException {
        var main = Cosmo42Application.class.getDeclaredMethod("main", String[].class);

        assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
    }
}
