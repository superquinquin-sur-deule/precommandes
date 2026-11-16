package fr.sqq.achatgroupe.acceptance.support;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Faux endpoint Brevo ({@code POST /smtp/email}) exposé par l'application de test : le client
 * REST Brevo y est redirigé en profil test (voir application.properties) et chaque email reçu
 * est conservé pour les assertions.
 */
@Path("/fake-brevo/smtp/email")
public class FakeBrevo {

    public record ReceivedEmail(String apiKey, JsonNode body) {
        public String recipient() {
            return body.path("to").path(0).path("email").asText();
        }
    }

    @ApplicationScoped
    public static class Mailbox {
        private final List<ReceivedEmail> emails = new CopyOnWriteArrayList<>();

        void add(ReceivedEmail email) {
            emails.add(email);
        }

        public List<ReceivedEmail> sentTo(String recipient) {
            return emails.stream().filter(e -> e.recipient().equalsIgnoreCase(recipient)).toList();
        }
    }

    @Inject
    Mailbox mailbox;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String send(@HeaderParam("api-key") String apiKey, JsonNode body) {
        mailbox.add(new ReceivedEmail(apiKey, body));
        return "{\"messageId\":\"<fake@brevo>\"}";
    }
}
