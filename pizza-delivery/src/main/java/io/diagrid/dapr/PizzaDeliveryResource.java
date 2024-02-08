package io.diagrid.dapr;

import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.type.Date;

import io.dapr.client.domain.Metadata;
import io.quarkiverse.dapr.core.SyncDaprClient;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/")
public class PizzaDeliveryResource {

    private static final String MESSAGE_TTL_IN_SECONDS = "1000";
    private static final Logger LOGGER = LoggerFactory.getLogger(PizzaDeliveryResource.class);
    private String pubsubName;
    private String pubsubTopic;
    private SyncDaprClient daprClient;
    private ObjectMapper objectMapper;

    public PizzaDeliveryResource(
            SyncDaprClient daprClient,
            ObjectMapper objectMapper,
            @ConfigProperty(name = "pubsub.name") String pubsubName,
            @ConfigProperty(name = "pubsub.topic") String pubsubTopic) {
        this.daprClient = daprClient;
        this.pubsubName = pubsubName;
        this.pubsubTopic = pubsubTopic;
        this.objectMapper = objectMapper;
    }

    @PUT
    @Consumes("application/json")
    @Path("/deliver")
    public Response deliveOrder(final String req) {
        LOGGER.info("Received order {}", req);
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Emit Event
                Order order = null;
                try {
                    order = objectMapper.readValue(req, Order.class);
                } catch (Exception e) {
                    LOGGER.error("Error {}", e);
                }

                Event event = new Event(EventType.ORDER_ON_ITS_WAY, order, "delivery",
                        "The order is on its way to your address.");
                emitEvent(event);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                event = new Event(EventType.ORDER_ON_ITS_WAY, order, "delivery", "The order is 1 mile away.");
                emitEvent(event);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                event = new Event(EventType.ORDER_ON_ITS_WAY, order, "delivery", "The order is 0.5 miles away.");
                emitEvent(event);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                event = new Event(EventType.ORDER_COMPLETED, order, "delivery", "Your order has been delivered.");
                emitEvent(event);

            }
        }).start();
        LOGGER.info("Returning response 200");
        return Response.ok().build();
    }

    public record Event(@JsonProperty EventType type, @JsonProperty Order order, @JsonProperty String service,
            @JsonProperty String message) {
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

    public record Order(@JsonProperty String id, @JsonProperty List<OrderItem> items, @JsonProperty Date orderDate) {
    }

    public record OrderItem(@JsonProperty PizzaType type, @JsonProperty int amount) {
    }

    public enum PizzaType {
        pepperoni, margherita, hawaiian, vegetarian
    }

    private void emitEvent(Event event) {
        LOGGER.info("> Emitting Delivery Event: {}", event);

        this.daprClient.publishEvent(pubsubName,
                pubsubTopic,
                event,
                Map.of(Metadata.TTL_IN_SECONDS, MESSAGE_TTL_IN_SECONDS));
    }
}
