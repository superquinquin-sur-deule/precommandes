package fr.sqq.achatgroupe.application.handler.command;

import fr.sqq.achatgroupe.application.command.CreateOrderCommand;
import fr.sqq.achatgroupe.application.notification.OrderConfirmation;
import fr.sqq.achatgroupe.application.port.out.OrderRepository;
import fr.sqq.achatgroupe.application.port.out.ProductRepository;
import fr.sqq.achatgroupe.application.port.out.TimeSlotRepository;
import fr.sqq.achatgroupe.application.port.out.VenteRepository;
import fr.sqq.achatgroupe.domain.exception.InsufficientStockException;
import fr.sqq.achatgroupe.domain.model.catalog.Product;
import fr.sqq.achatgroupe.domain.model.catalog.ProductVariant;
import fr.sqq.achatgroupe.domain.model.order.CustomerInfo;
import fr.sqq.achatgroupe.domain.model.order.Order;
import fr.sqq.achatgroupe.domain.model.order.OrderItem;
import fr.sqq.achatgroupe.domain.model.order.OrderNumber;
import fr.sqq.achatgroupe.domain.model.planning.TimeSlot;
import fr.sqq.achatgroupe.domain.model.shared.Money;
import fr.sqq.achatgroupe.domain.model.vente.PickupMode;
import fr.sqq.achatgroupe.domain.model.vente.Vente;
import fr.sqq.achatgroupe.domain.model.vente.VenteId;
import fr.sqq.achatgroupe.domain.model.vente.VenteStatus;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateOrderHandlerTest {

    private static final Long VENTE_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long VARIANT_ID = 110L;
    private static final Long TIME_SLOT_ID = 5L;
    private static final LocalDate PICKUP_DAY = LocalDate.of(2026, 12, 12);

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private TimeSlotRepository timeSlotRepository;
    private VenteRepository venteRepository;
    private Event<OrderConfirmation> orderConfirmed;
    private CreateOrderHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        orderRepository = Mockito.mock(OrderRepository.class);
        productRepository = Mockito.mock(ProductRepository.class);
        timeSlotRepository = Mockito.mock(TimeSlotRepository.class);
        venteRepository = Mockito.mock(VenteRepository.class);
        orderConfirmed = Mockito.mock(Event.class);
        handler = new CreateOrderHandler(orderRepository, productRepository, timeSlotRepository,
                venteRepository, orderConfirmed);

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findByVariantId(VARIANT_ID)).thenReturn(Optional.of(product(10)));
    }

    @Test
    void should_publish_order_confirmation_with_order_details_and_time_slot() {
        givenVente(PickupMode.TIME_SLOTS, null, null);
        when(timeSlotRepository.findSlotById(TIME_SLOT_ID)).thenReturn(Optional.of(
                new TimeSlot(TIME_SLOT_ID, VENTE_ID, PICKUP_DAY, LocalTime.of(10, 0), LocalTime.of(11, 0), 30, 0)));

        Order order = handler.handle(command(TIME_SLOT_ID, null, 3));

        OrderConfirmation confirmation = capturedConfirmation();
        assertThat(confirmation.orderId()).isEqualTo(order.id());
        assertThat(confirmation.orderNumber()).isEqualTo(order.orderNumber().value());
        assertThat(confirmation.customerEmail()).isEqualTo("alice@test.com");
        assertThat(confirmation.customerFirstName()).isEqualTo("Alice");
        assertThat(confirmation.venteName()).isEqualTo("Vente de Noël");
        assertThat(confirmation.timeSlot()).isEqualTo(new OrderConfirmation.TimeSlot(
                PICKUP_DAY, LocalTime.of(10, 0), LocalTime.of(11, 0)));
        assertThat(confirmation.pickupStartDate()).isNull();
        assertThat(confirmation.lines()).singleElement().satisfies(line -> {
            assertThat(line.productName()).isEqualTo("Tomates");
            assertThat(line.quantity()).isEqualTo(3);
            assertThat(line.unitPrice()).isEqualByComparingTo(order.items().get(0).unitPrice().amount());
            assertThat(line.subtotal()).isEqualByComparingTo(order.items().get(0).subtotal().amount());
        });
        assertThat(confirmation.totalAmount()).isEqualByComparingTo(order.totalAmount().amount());
    }

    @Test
    void should_publish_pickup_range_when_vente_has_no_time_slots() {
        givenVente(PickupMode.DATE_RANGE, PICKUP_DAY, PICKUP_DAY.plusDays(3));

        handler.handle(command(null, null, 1));

        OrderConfirmation confirmation = capturedConfirmation();
        assertThat(confirmation.timeSlot()).isNull();
        assertThat(confirmation.pickupStartDate()).isEqualTo(PICKUP_DAY);
        assertThat(confirmation.pickupEndDate()).isEqualTo(PICKUP_DAY.plusDays(3));
    }

    @Test
    void should_not_publish_confirmation_again_when_order_is_replayed_with_same_idempotency_key() {
        Order existing = Order.createWithoutTimeSlot(VENTE_ID, OrderNumber.generate(),
                new CustomerInfo("Alice", "Dupont", "alice@test.com", "0601020304"),
                List.of(OrderItem.create(PRODUCT_ID, VARIANT_ID, 1, Money.eur(new BigDecimal("3.50")))), "key-1");
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        Order order = handler.handle(command(null, "key-1", 1));

        assertThat(order).isSameAs(existing);
        verify(orderConfirmed, never()).fire(any());
    }

    @Test
    void should_not_publish_confirmation_when_order_fails() {
        givenVente(PickupMode.DATE_RANGE, PICKUP_DAY, PICKUP_DAY);

        assertThatThrownBy(() -> handler.handle(command(null, null, 50)))
                .isInstanceOf(InsufficientStockException.class);
        verify(orderConfirmed, never()).fire(any());
    }

    private OrderConfirmation capturedConfirmation() {
        ArgumentCaptor<OrderConfirmation> captor = ArgumentCaptor.forClass(OrderConfirmation.class);
        verify(orderConfirmed).fire(captor.capture());
        return captor.getValue();
    }

    private void givenVente(PickupMode mode, LocalDate pickupStart, LocalDate pickupEnd) {
        when(venteRepository.findById(new VenteId(VENTE_ID))).thenReturn(Optional.of(
                new Vente(VENTE_ID, "Vente de Noël", null, VenteStatus.ACTIVE, null, null,
                        mode, pickupStart, pickupEnd, Instant.now())));
    }

    private static CreateOrderCommand command(Long timeSlotId, String idempotencyKey, int quantity) {
        return new CreateOrderCommand(VENTE_ID, "Alice", "Dupont", "alice@test.com", "0601020304",
                timeSlotId, idempotencyKey, List.of(new CreateOrderCommand.OrderItemCommand(VARIANT_ID, quantity)));
    }

    private static Product product(int stock) {
        return new Product(PRODUCT_ID, VENTE_ID, "Tomates", "Bio", new BigDecimal("5.5"), "Ferme A", true,
                "Legumes", "Ferme A", null, false,
                List.of(new ProductVariant(VARIANT_ID, PRODUCT_ID, "", "TOM-001",
                        Money.eur(new BigDecimal("3.00")), stock, null, 0)));
    }
}
