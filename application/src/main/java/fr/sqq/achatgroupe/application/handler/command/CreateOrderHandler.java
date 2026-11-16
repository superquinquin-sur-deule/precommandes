package fr.sqq.achatgroupe.application.handler.command;

import fr.sqq.achatgroupe.application.command.CreateOrderCommand;
import fr.sqq.achatgroupe.application.notification.OrderConfirmation;
import fr.sqq.achatgroupe.application.port.out.OrderRepository;
import fr.sqq.achatgroupe.application.port.out.ProductRepository;
import fr.sqq.achatgroupe.application.port.out.TimeSlotRepository;
import fr.sqq.achatgroupe.application.port.out.VenteRepository;
import fr.sqq.achatgroupe.domain.exception.VariantNotFoundException;
import fr.sqq.achatgroupe.domain.exception.TimeSlotNotFoundException;
import fr.sqq.achatgroupe.domain.exception.TimeSlotRequiredException;
import fr.sqq.achatgroupe.domain.exception.VenteNotFoundException;
import fr.sqq.achatgroupe.domain.model.catalog.Product;
import fr.sqq.achatgroupe.domain.model.catalog.ProductVariant;
import fr.sqq.achatgroupe.domain.model.order.CustomerInfo;
import fr.sqq.achatgroupe.domain.model.order.Order;
import fr.sqq.achatgroupe.domain.model.order.OrderItem;
import fr.sqq.achatgroupe.domain.model.order.OrderNumber;
import fr.sqq.achatgroupe.domain.model.planning.TimeSlot;
import fr.sqq.achatgroupe.domain.model.vente.Vente;
import fr.sqq.achatgroupe.domain.model.vente.VenteId;
import fr.sqq.mediator.CommandHandler;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CreateOrderHandler implements CommandHandler<CreateOrderCommand, Order> {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final VenteRepository venteRepository;
    private final Event<OrderConfirmation> orderConfirmed;

    public CreateOrderHandler(OrderRepository orderRepository, ProductRepository productRepository,
                              TimeSlotRepository timeSlotRepository, VenteRepository venteRepository,
                              Event<OrderConfirmation> orderConfirmed) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.venteRepository = venteRepository;
        this.orderConfirmed = orderConfirmed;
    }

    @Override
    @Transactional
    public Order handle(CreateOrderCommand command) {
        Log.infof("Creating order for %s", command.email());
        if (command.idempotencyKey() != null) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        VenteId venteId = new VenteId(command.venteId());
        Vente vente = venteRepository.findById(venteId)
                .orElseThrow(() -> new VenteNotFoundException(venteId));

        // En mode plage de dates, aucun créneau n'est réservé : le retrait se fait
        // librement pendant la période configurée sur la vente.
        TimeSlot timeSlot = null;
        if (vente.usesTimeSlots()) {
            if (command.timeSlotId() == null) {
                throw new TimeSlotRequiredException();
            }
            timeSlot = timeSlotRepository.findSlotById(command.timeSlotId())
                    .orElseThrow(() -> new TimeSlotNotFoundException(command.timeSlotId()));
            timeSlot.reserveOnePlace();
        }

        List<OrderItem> orderItems = new ArrayList<>();
        List<OrderConfirmation.Line> confirmationLines = new ArrayList<>();
        for (CreateOrderCommand.OrderItemCommand itemCmd : command.items()) {
            Product product = productRepository.findByVariantId(itemCmd.variantId())
                    .orElseThrow(() -> new VariantNotFoundException(itemCmd.variantId()));

            if (!product.active()) {
                throw new VariantNotFoundException(itemCmd.variantId());
            }

            ProductVariant variant = product.variant(itemCmd.variantId());
            variant.decrementStock(itemCmd.quantity(), product.displayName(variant));
            productRepository.save(product);

            OrderItem item = OrderItem.create(product.id(), variant.id(), itemCmd.quantity(), product.prixTtc(variant));
            orderItems.add(item);
            confirmationLines.add(new OrderConfirmation.Line(product.displayName(variant), item.quantity(),
                    item.unitPrice().amount(), item.subtotal().amount()));
        }

        if (timeSlot != null) {
            timeSlotRepository.save(timeSlot);
        }

        CustomerInfo customer = new CustomerInfo(command.customerFirstName(), command.customerLastName(), command.email(), command.phone());
        OrderNumber orderNumber = OrderNumber.generate();
        Order order = vente.usesTimeSlots()
                ? Order.create(command.venteId(), orderNumber, customer, command.timeSlotId(), orderItems, command.idempotencyKey())
                : Order.createWithoutTimeSlot(command.venteId(), orderNumber, customer, orderItems, command.idempotencyKey());

        Order saved = orderRepository.save(order);
        // Observé après commit uniquement : pas d'email si la transaction échoue.
        orderConfirmed.fire(toConfirmation(saved, vente, timeSlot, confirmationLines));
        return saved;
    }

    private static OrderConfirmation toConfirmation(Order order, Vente vente, TimeSlot timeSlot,
                                                    List<OrderConfirmation.Line> lines) {
        CustomerInfo customer = order.customer();
        OrderConfirmation.TimeSlot slot = timeSlot != null
                ? new OrderConfirmation.TimeSlot(timeSlot.date(), timeSlot.startTime(), timeSlot.endTime())
                : null;
        return new OrderConfirmation(
                order.id(),
                order.orderNumber().value(),
                customer.firstName(),
                customer.lastName(),
                customer.email(),
                vente.name(),
                slot,
                vente.pickupStartDate(),
                vente.pickupEndDate(),
                lines,
                order.totalAmount().amount());
    }
}
