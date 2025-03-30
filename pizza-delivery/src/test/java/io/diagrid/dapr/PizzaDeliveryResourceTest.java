package io.diagrid.dapr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.domain.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.http.entity.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
class PizzaDeliveryResourceTest {

   @Inject
   ObjectMapper mapper;

   @Inject
   SubscriptionResource subscriptionResource;

   @Test
   void should_call_deliver_endpoint_correctly() throws JsonProcessingException, InterruptedException {
      // given
      List<PizzaDeliveryResource.OrderItem> orderItems = List.of(
            new PizzaDeliveryResource.OrderItem(PizzaDeliveryResource.PizzaType.pepperoni, 100));
      Date now = new Date();

      PizzaDeliveryResource.Order order = new PizzaDeliveryResource.Order(UUID.randomUUID().toString(), orderItems,
            now);

      String requestBody = this.mapper.writeValueAsString(order);

      // when
      given().contentType(ContentType.APPLICATION_JSON.getMimeType()).body(requestBody).when().put("/deliver").then()
            .statusCode(200);
      
      Awaitility.await().atMost(Duration.ofMinutes(1)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() -> {
         List<CloudEvent<PizzaDeliveryResource.Event>> events = subscriptionResource.getAllEvents();
         Assertions.assertEquals(4, events.size(), "Four published event are expected");
         Assertions.assertEquals(PizzaDeliveryResource.EventType.ORDER_ON_ITS_WAY, events.get(0).getData().type(),
               "The content of the cloud event should be the order-out-on-its-way event");
         Assertions.assertEquals(PizzaDeliveryResource.EventType.ORDER_ON_ITS_WAY, events.get(1).getData().type(),
               "The content of the cloud event should be the order-out-on-its-way event");
         Assertions.assertEquals(PizzaDeliveryResource.EventType.ORDER_ON_ITS_WAY, events.get(2).getData().type(),
               "The content of the cloud event should be the order-out-on-its-way event");
         Assertions.assertEquals(PizzaDeliveryResource.EventType.ORDER_COMPLETED, events.get(3).getData().type(),
               "The content of the cloud event should be the order-completed event");
      });
   }
}
