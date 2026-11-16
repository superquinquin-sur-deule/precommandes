package fr.sqq.achatgroupe.application.notification;

import fr.sqq.achatgroupe.application.port.out.OrderConfirmationSender;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class OrderConfirmationListenerTest {

    private final OrderConfirmationSender sender = Mockito.mock(OrderConfirmationSender.class);
    private final OrderConfirmationListener listener = new OrderConfirmationListener(sender);

    private final OrderConfirmation confirmation = new OrderConfirmation(UUID.randomUUID(), "AG-2026-000010001",
            "Alice", "Dupont", "alice@test.com", "Vente de Noël", null,
            LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 15),
            List.of(new OrderConfirmation.Line("Tomates", 2, new BigDecimal("3.17"), new BigDecimal("6.34"))),
            new BigDecimal("6.34"));

    @Test
    void should_send_confirmation_email() {
        listener.onOrderConfirmed(confirmation);

        verify(sender).send(confirmation);
    }

    @Test
    void should_not_propagate_email_failure() {
        doThrow(new RuntimeException("Brevo indisponible")).when(sender).send(any());

        assertThatCode(() -> listener.onOrderConfirmed(confirmation)).doesNotThrowAnyException();
    }
}
