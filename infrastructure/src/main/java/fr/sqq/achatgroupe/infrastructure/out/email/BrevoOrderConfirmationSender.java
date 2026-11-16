package fr.sqq.achatgroupe.infrastructure.out.email;

import fr.sqq.achatgroupe.application.notification.OrderConfirmation;
import fr.sqq.achatgroupe.application.port.out.OrderConfirmationSender;
import io.quarkus.logging.Log;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class BrevoOrderConfirmationSender implements OrderConfirmationSender {

    static final String TAG = "order-confirmation";

    private final BrevoClient brevoClient;
    private final EmailConfig config;
    private final Template html;
    private final Template text;

    public BrevoOrderConfirmationSender(@RestClient BrevoClient brevoClient,
                                        EmailConfig config,
                                        @Location("emails/order-confirmation.html") Template html,
                                        @Location("emails/order-confirmation.txt") Template text) {
        this.brevoClient = brevoClient;
        this.config = config;
        this.html = html;
        this.text = text;
    }

    /** Échoue au démarrage plutôt qu'à la première commande si l'envoi est activé sans être configuré. */
    void validateConfig(@Observes StartupEvent event) {
        if (!config.enabled()) {
            Log.warn("Envoi d'emails désactivé (app.email.enabled=false) : les confirmations de commande ne seront que journalisées");
            return;
        }
        if (config.brevo().apiKey().filter(k -> !k.isBlank()).isEmpty()) {
            throw new IllegalStateException("app.email.brevo.api-key est requis quand app.email.enabled=true");
        }
        if (config.sender().email().filter(e -> !e.isBlank()).isEmpty()) {
            throw new IllegalStateException("app.email.sender.email est requis quand app.email.enabled=true");
        }
    }

    @Override
    public void send(OrderConfirmation confirmation) {
        if (!config.enabled()) {
            Log.infof("Email de confirmation de la commande %s non envoyé (envoi désactivé)", confirmation.orderNumber());
            return;
        }

        var email = OrderConfirmationEmail.from(confirmation);
        var request = new BrevoEmailRequest(
                new BrevoEmailRequest.Contact(config.sender().email().orElseThrow(), config.sender().name()),
                List.of(new BrevoEmailRequest.Contact(confirmation.customerEmail(), fullName(confirmation))),
                "Confirmation de ta commande " + confirmation.orderNumber(),
                html.data("email", email).render(),
                text.data("email", email).render(),
                List.of(TAG));

        brevoClient.sendTransactionalEmail(config.brevo().apiKey().orElseThrow(), request);
        Log.infof("Email de confirmation de la commande %s envoyé", confirmation.orderNumber());
    }

    private static String fullName(OrderConfirmation c) {
        return c.customerLastName() == null || c.customerLastName().isBlank()
                ? c.customerFirstName()
                : c.customerFirstName() + " " + c.customerLastName();
    }
}
