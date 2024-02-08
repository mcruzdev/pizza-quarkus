package io.diagrid.dapr;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import io.dapr.client.domain.CloudEvent;
import io.dapr.client.domain.State;
import io.quarkiverse.dapr.core.SyncDaprClient;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path(value = "/")
public class PizzaResource {

    static final String KEY = "orders";
    static final Logger LOGGER = LoggerFactory.getLogger(PizzaResource.class);

    private SyncDaprClient daprClient;
    private String stateStoreName;
    private String publicIp;
    private DeliveryRestClient deliveryRestClient;
    private KitchenRestClient kitchenRestClient;
    private Template index;

    public PizzaResource(
            SyncDaprClient daprClient,
            @RestClient DeliveryRestClient deliveryRestClient,
            @RestClient KitchenRestClient kitchenRestClient,
            @ConfigProperty(name = "state.store.name") String stateStoreName,
            @ConfigProperty(name = "public.ip") String publicIp,
            Template index) {
        this.daprClient = daprClient;
        this.kitchenRestClient = kitchenRestClient;
        this.deliveryRestClient = deliveryRestClient;
        this.stateStoreName = stateStoreName;
        this.publicIp = publicIp;
        this.index = index;
    }

    @GET
    @Path("/")
    @Produces(value = MediaType.TEXT_HTML)
    public TemplateInstance page() {
        return index.data("event", "Sou Java + Ifood");
    }

    @GET
    @Path("/server-info")
    public Info getInfo() {
        return new Info(this.publicIp);
    }

    @POST
    @Path("/events")
    @Consumes(value = "application/cloudevents+json")
    public void receiveEvents(CloudEvent<Event> event) {

        LOGGER.info("Received CloudEvent via Subscription: {}", event);

        Event pizzaEvent = event.getData();

        if (pizzaEvent.type.equals(EventType.ORDER_READY)) {
            prepareOrderForDelivery(pizzaEvent.order);
        }
    }

    @POST
    @Path("/order")
    public Response placeOrder(Order order)
            throws Exception {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Emit Event
                Event event = new Event(EventType.ORDER_PLACED, order, "store",
                        "We received the payment your order is confirmed.");
                emitWSEvent(event);

                // Store Order
                store(order);

                // Process Order, sent to kitcken
                callKitchenService(order);
            }
        }).start();

        return Response.ok(order).build();

    }

    private void callKitchenService(Order order) {
        LOGGER.info("Calling Kitchen service at: '/prepare' through Dapr");
        kitchenRestClient.call(order);
    }

    private void prepareOrderForDelivery(Order order) {
        store(new Order(order.id, order.customer, order.items, order.orderDate, Status.delivery));
        // Emit Event
        Event event = new Event(EventType.ORDER_OUT_FOR_DELIVERY, order, "store", "Delivery in progress.");
        emitWSEvent(event);

        callDeliveryService(order);
    }

    private void emitWSEvent(Event event) {
        LOGGER.info("Emitting Event via WS: {}", event.toString());
    }

    private void store(Order order) {

        Orders orders = new Orders(new ArrayList<Order>());
        State<Orders> ordersState = null;
        try {
            ordersState = daprClient.getState(stateStoreName, KEY, null, Orders.class);
        } catch (Exception e) {
            // Github issue https://github.com/quarkiverse/quarkus-dapr/issues/146
            // Waiting approval https://github.com/quarkiverse/quarkus-dapr/pull/156
        }

        if (ordersState == null) {
            orders.orders.add(order);
        } else if (ordersState.getValue() != null && ordersState.getValue().orders.isEmpty()) {
            orders.orders.addAll(ordersState.getValue().orders);
        }

        // Save state
        daprClient.saveState(stateStoreName, KEY, orders);

    }

    private void callDeliveryService(Order order) {
        LOGGER.info("Calling Delivery service at: '/deliver' through Dapr");
        deliveryRestClient.call(order);
    }

    public enum EventType {

        ORDER_PLACED("order-placed"),
        ITEMS_IN_STOCK("items-in-stock"),
        ITEMS_NOT_IN_STOCK("items-not-in-stock"),
        ORDER_IN_PREPARATION("order-in-preparation"),
        ORDER_READY("order-ready"),
        ORDER_OUT_FOR_DELIVERY("order-out-for-delivery"),
        ORDER_ON_ITS_WAY("order-on-its-way"),
        ORDER_COMPLETED("order-completed");

        private String type;

        EventType(String type) {
            this.type = type;
        }

        @JsonValue
        public String getType() {
            return type;
        }
    }

    private record Orders(@JsonProperty List<Order> orders) {
    }

    public record Order(@JsonProperty String id, @JsonProperty Customer customer, @JsonProperty List<OrderItem> items,
            @JsonProperty Date orderDate, @JsonProperty Status status) {

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public Order(String id, Customer customer, List<OrderItem> items, Date orderDate, Status status) {
            if (id == null) {
                this.id = UUID.randomUUID().toString();
            } else {
                this.id = id;
            }
            this.customer = customer;
            this.items = items;
            if (orderDate == null) {
                this.orderDate = new Date();
            } else {
                this.orderDate = orderDate;
            }
            if (status == null) {
                this.status = Status.created;
            } else {
                this.status = status;
            }
        }

        public Order(Customer customer, List<OrderItem> items, Date orderDate, Status status) {
            this(UUID.randomUUID().toString(), customer, items, orderDate, status);
        }

        public Order(Customer customer, List<OrderItem> items) {
            this(UUID.randomUUID().toString(), customer, items, new Date(), Status.created);
        }

        public Order(Order order) {
            this(order.id, order.customer, order.items, order.orderDate, order.status);
        }
    }

    public record Customer(@JsonProperty String name, @JsonProperty String email) {
    }

    public record OrderItem(@JsonProperty PizzaType type, @JsonProperty int amount) {
    }

    public enum PizzaType {
        pepperoni, margherita, hawaiian, vegetarian, kubernetescheese, daprcheese, clustertomatoes, diagridpepperoni,
        distributedolives, opensauce, workflowspread, plantbasedobservability, bindingsbacon
    }

    public enum Status {
        created, placed, notplaced, instock, notinstock, inpreparation, delivery, completed, failed
    }

    public record Event(EventType type, Order order, String service, String message) {
    }

    public record Info(String publicIp) {
    }

}
