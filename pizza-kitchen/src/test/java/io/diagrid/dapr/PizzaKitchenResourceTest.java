package io.diagrid.dapr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.domain.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;

@QuarkusTest
public class PizzaKitchenResourceTest {

   @Inject
   SubscriptionResource subscriptionResource;

   @Inject
   ObjectMapper objectMapper;

   @Test
   public void should_prepare_order_correctly() throws JsonProcessingException {
      // given
      PizzaKitchenResource.Order order = new PizzaKitchenResource.Order(UUID.randomUUID().toString(),
            Arrays.asList(new PizzaKitchenResource.OrderItem(PizzaKitchenResource.PizzaType.pepperoni, 1)), new Date());

      String body = objectMapper.writeValueAsString(order);

      given().body(body).contentType(ContentType.JSON).when().request("PUT", "/prepare").then().assertThat()
            .statusCode(200);

      // act, assert
      Awaitility.await()
            .atMost(Duration.ofMinutes(1))
            .pollInterval(Duration.ofSeconds(5))
            .untilAsserted(() -> {
         List<CloudEvent<PizzaKitchenResource.Event>> allEvents = subscriptionResource.getAllEvents();
         Assertions.assertEquals(2, allEvents.size(), "Two published event are expected");
         Assertions.assertEquals(PizzaKitchenResource.EventType.ORDER_IN_PREPARATION, allEvents.get(0).getData().type(),
               "The content of the cloud event should be the in preparation event");
         Assertions.assertEquals(PizzaKitchenResource.EventType.ORDER_READY, allEvents.get(1).getData().type(),
               "The content of the cloud event should be the ready event");
      });
   }
}
