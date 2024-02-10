package io.diagrid.dapr;

import io.dapr.Topic;
import io.dapr.client.domain.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Path("/")
@ApplicationScoped
public class SubscriptionResource {

    static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionResource.class);
    List<CloudEvent<PizzaDeliveryResource.Event>> events = new ArrayList<>();

    @POST
    @Path("/events")
    @Topic(pubsubName = "pubsub", name = "topic")
    @Consumes(value = {"application/cloudevents+json", "application/json"})
    public void receiveEvents(CloudEvent<PizzaDeliveryResource.Event> event) {
        LOGGER.info("Receiving event from Dapr running on Dev Services");
        events.add(event);
    }

    @GET
    @Path("/events")
    @Consumes(value = {"application/cloudevents+json", "application/json"})
    public List<CloudEvent<PizzaDeliveryResource.Event>> getAllEvents() {
        return events;
    }

}
