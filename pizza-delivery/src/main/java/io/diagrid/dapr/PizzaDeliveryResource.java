package io.diagrid.dapr;

import java.util.Collections;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.type.Date;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.client.domain.Metadata;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/")
public class PizzaDeliveryResource {

    private static final String MESSAGE_TTL_IN_SECONDS = "1000";

    private String pubsubName;
    private String pubsubTopic;

    public PizzaDeliveryResource(
            @ConfigProperty(name = "pubsub.name") String pubsubName,
            @ConfigProperty(name = "pubsub.topic") String pubsubTopic) {
        this.pubsubName = pubsubName;
        this.pubsubTopic = pubsubTopic;
    }

    @PUT
    @Path("/deliver")
    public Response deliveOrder(Order order) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Emit Event
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

        return Response.ok().build();
    }

    public record Event(EventType type, Order order, String service, String message) {
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
        System.out.println("> Emitting Delivery Event: " + event.toString());
        try (DaprClient client = (new DaprClientBuilder()).build()) {
            client.publishEvent(pubsubName,
                    pubsubTopic,
                    event,
                    Collections.singletonMap(Metadata.TTL_IN_SECONDS, MESSAGE_TTL_IN_SECONDS)).block();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
