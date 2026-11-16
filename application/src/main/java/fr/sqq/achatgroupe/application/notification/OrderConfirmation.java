package fr.sqq.achatgroupe.application.notification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Récapitulatif d'une commande tout juste confirmée, avec tout ce qu'il faut pour
 * prévenir le coopérateur (libellés produits, modalités de retrait), sans avoir à
 * recharger les agrégats une fois la transaction terminée.
 *
 * <p>Le retrait se fait soit sur un créneau ({@code timeSlot}), soit librement sur
 * une plage de dates ({@code pickupStartDate} / {@code pickupEndDate}) : un seul des
 * deux est renseigné.
 */
public record OrderConfirmation(
        UUID orderId,
        String orderNumber,
        String customerFirstName,
        String customerLastName,
        String customerEmail,
        String venteName,
        TimeSlot timeSlot,
        LocalDate pickupStartDate,
        LocalDate pickupEndDate,
        List<Line> lines,
        BigDecimal totalAmount
) {

    public OrderConfirmation {
        lines = List.copyOf(lines);
    }

    public record TimeSlot(LocalDate date, LocalTime startTime, LocalTime endTime) {
    }

    public record Line(String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
