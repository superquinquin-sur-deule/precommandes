package fr.sqq.achatgroupe.infrastructure.out.email;

import fr.sqq.achatgroupe.application.notification.OrderConfirmation;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Données déjà formatées (dates, montants en français) consommées par les gabarits Qute. */
public record OrderConfirmationEmail(
        String orderNumber,
        String customerFirstName,
        String venteName,
        String pickupLabel,
        String pickupInstructions,
        List<Line> lines,
        String total
) {

    private static final Locale FR = Locale.FRANCE;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", FR);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH'h'mm", FR);

    public record Line(String productName, int quantity, String unitPrice, String subtotal) {
    }

    public static OrderConfirmationEmail from(OrderConfirmation confirmation) {
        var lines = confirmation.lines().stream()
                .map(l -> new Line(l.productName(), l.quantity(), price(l.unitPrice()), price(l.subtotal())))
                .toList();
        return new OrderConfirmationEmail(
                confirmation.orderNumber(),
                confirmation.customerFirstName(),
                confirmation.venteName(),
                pickupLabel(confirmation),
                confirmation.timeSlot() != null
                        ? "Présente-toi au créneau indiqué pour récupérer et régler ta commande."
                        : "Présente-toi en magasin pendant cette période pour récupérer et régler ta commande.",
                lines,
                price(confirmation.totalAmount()));
    }

    private static String pickupLabel(OrderConfirmation c) {
        if (c.timeSlot() != null) {
            var slot = c.timeSlot();
            return date(slot.date()) + " · " + slot.startTime().format(TIME) + " – " + slot.endTime().format(TIME);
        }
        if (c.pickupStartDate() == null || c.pickupEndDate() == null) {
            return null;
        }
        if (c.pickupStartDate().equals(c.pickupEndDate())) {
            return "Retrait le " + date(c.pickupStartDate());
        }
        return "Retrait du " + date(c.pickupStartDate()) + " au " + date(c.pickupEndDate());
    }

    private static String date(LocalDate date) {
        return date.format(DATE);
    }

    private static String price(BigDecimal amount) {
        // Espaces insécables fines (NNBSP) remplacées : mal rendues par certains clients mail.
        return NumberFormat.getCurrencyInstance(FR).format(amount).replace(' ', ' ');
    }
}
