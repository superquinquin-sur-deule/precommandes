package fr.sqq.achatgroupe.application.port.out;

import fr.sqq.achatgroupe.application.notification.OrderConfirmation;

/** Envoie au coopérateur l'email récapitulatif de sa commande. */
public interface OrderConfirmationSender {

    void send(OrderConfirmation confirmation);
}
