package fr.sqq.achatgroupe.application.notification;

import fr.sqq.achatgroupe.application.port.out.OrderConfirmationSender;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

/**
 * Envoie l'email de confirmation une fois la commande réellement enregistrée.
 *
 * <p>L'envoi n'a lieu qu'après le commit ({@link TransactionPhase#AFTER_SUCCESS}) : une
 * commande annulée par un rollback (stock épuisé, créneau complet, conflit de version
 * rejoué par {@code @Retry}) ne déclenche aucun email. Un échec d'envoi est journalisé
 * mais ne remet jamais en cause la commande, déjà validée.
 */
@ApplicationScoped
public class OrderConfirmationListener {

    private final OrderConfirmationSender sender;

    public OrderConfirmationListener(OrderConfirmationSender sender) {
        this.sender = sender;
    }

    void onOrderConfirmed(@Observes(during = TransactionPhase.AFTER_SUCCESS) OrderConfirmation confirmation) {
        try {
            sender.send(confirmation);
        } catch (RuntimeException e) {
            Log.errorf(e, "Échec de l'envoi de l'email de confirmation de la commande %s",
                    confirmation.orderNumber());
        }
    }
}
