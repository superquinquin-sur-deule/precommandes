package fr.sqq.achatgroupe.infrastructure.out.email;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "app.email")
public interface EmailConfig {

    /** Désactivé : les emails sont seulement journalisés, rien n'est envoyé à Brevo. */
    @WithDefault("false")
    boolean enabled();

    Brevo brevo();

    Sender sender();

    interface Brevo {
        Optional<String> apiKey();
    }

    interface Sender {
        Optional<String> email();

        @WithDefault("Achat Groupé")
        String name();
    }
}
