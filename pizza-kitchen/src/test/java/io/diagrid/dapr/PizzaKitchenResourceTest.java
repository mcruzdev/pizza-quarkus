package io.diagrid.dapr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.domain.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;

@QuarkusTest
public class PizzaKitchenResourceTest {

    @Inject
    SubscriptionResource subscriptionResource;

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void should_prepare_order_correctly() throws InterruptedException, JsonProcessingException {
        // given
        PizzaKitchenResource.Order order = new PizzaKitchenResource.Order(UUID.randomUUID().toString(),
                Arrays.asList(new PizzaKitchenResource.OrderItem(PizzaKitchenResource.PizzaType.pepperoni, 1)),
                new Date());

        String body = objectMapper.writeValueAsString(order);

        given().body(body)
                .contentType(ContentType.JSON)
                .when()
                .request("PUT", "/prepare")
                .then().assertThat().statusCode(200);

        // Wait for the event to arrive
        Thread.sleep(17000);

        List<CloudEvent<PizzaKitchenResource.Event>> events = subscriptionResource.getAllEvents();
        assertEquals("Two published event are expected", 2, events.size());
        assertEquals("The content of the cloud event should be the in preparation event", PizzaKitchenResource.EventType.ORDER_IN_PREPARATION, events.get(0).getData().type());
        assertEquals("The content of the cloud event should be the ready event", PizzaKitchenResource.EventType.ORDER_READY, events.get(1).getData().type());

    }
}