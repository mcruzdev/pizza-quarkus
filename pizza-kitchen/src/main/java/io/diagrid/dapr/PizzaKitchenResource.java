package io.diagrid.dapr;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import io.dapr.client.domain.Metadata;
import io.quarkiverse.dapr.core.SyncDaprClient;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/")
public class PizzaKitchenResource {

    private static final Random RANDOM = new Random();
    private static final int MS_IN_SECOND = 1000;
    private static final String MESSAGE_TTL_IN_SECONDS = "1000";
    private static final Logger LOGGER = LoggerFactory.getLogger(PizzaKitchenResource.class);

    private String topicName;
    private String pubSubName;
    private SyncDaprClient daprClient;

    public PizzaKitchenResource(
            final SyncDaprClient daprClient,
            final @ConfigProperty(name = "pubsub.name") String pubSubName,
            final @ConfigProperty(name = "pubsub.topic") String topicName) {
        this.daprClient = daprClient;
        this.pubSubName = pubSubName;
        this.topicName = topicName;
    }

    @PUT
    @Path("/prepare")
    public Response prepareOrder(final Order order) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Emit Event
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Event event = new Event(EventType.ORDER_IN_PREPARATION, order, "kitchen",
                        "The order is now in the kitchen.");
                emitEvent(event);
                for (OrderItem orderItem : order.items) {

                    int pizzaPrepTime = RANDOM.nextInt(15 * MS_IN_SECOND);
                    LOGGER.info("Preparing this {} pizza will take: {}", orderItem.type, pizzaPrepTime);
                    try {
                        Thread.sleep(pizzaPrepTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                event = new Event(EventType.ORDER_READY, order, "kitchen",
                        "Your pizza is ready and waiting to be delivered.");
                emitEvent(event);
            }
        }).start();

        return Response.ok().build();
    }

    private void emitEvent(Event event) {
        LOGGER.info("> Emitting Kitchen Event: {}", event.toString());

        daprClient.publishEvent(pubSubName,
                topicName,
                event,
                Collections.singletonMap(Metadata.TTL_IN_SECONDS, MESSAGE_TTL_IN_SECONDS));

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

    public record Event(EventType type, Order order, String service, String message) {
    }

    public record Order(@JsonProperty String id, @JsonProperty List<OrderItem> items, @JsonProperty Date orderDate) {
    }

    public record KitchenResponse(@JsonProperty String message, @JsonProperty String orderId) {
    }

    public record OrderItem(@JsonProperty PizzaType type, @JsonProperty int amount) {
    }

    public enum PizzaType {
        pepperoni, margherita, hawaiian, vegetarian
    }

    public record InventoryRequest(PizzaType pizzaType, int amount) {
    }
}
