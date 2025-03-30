package io.diagrid.dapr;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.dapr.core.SyncDaprClient;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@ConnectWireMock
class PizzaResourceTest {

   private static final LinkedBlockingDeque<String> MESSAGES = new LinkedBlockingDeque<>();
   WireMock wiremock;

   @Inject
   SyncDaprClient daprClient;
   @ConfigProperty(name = "state.store.name")
   String stateStoreName;

   @TestHTTPResource("/ws")
   URI uri;

   @BeforeEach
   void beforeEach() {
      MESSAGES.clear();
   }


   @Test
   void should_get_server_info_correctly() {
      given().accept(ContentType.JSON).get("/server-info").then().statusCode(200)
            .body("publicIp", Matchers.equalTo("localhost:8080"));
   }

   @Test
   void should_place_order_correctly() {
      // given
      PizzaResource.Order order = new PizzaResource.Order(new PizzaResource.Customer("John Doe", "john.doe@gmail.com"),
            List.of(new PizzaResource.OrderItem(PizzaResource.PizzaType.pepperoni, 1)));

      given().contentType(ContentType.JSON).body(order).post("/order").then().statusCode(200);
   }

   @Test
   void should_emit_message_to_socket_client_correctly() throws DeploymentException, IOException, InterruptedException {
      // given
      try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {

         wiremock.register(WireMock.put("/prepare").willReturn(aResponse().withStatus(200)));

         PizzaResource.Order order = new PizzaResource.Order(
               new PizzaResource.Customer("John Doe", "john.doe@gmail.com"),
               List.of(new PizzaResource.OrderItem(PizzaResource.PizzaType.pepperoni, 1)));

         given().contentType(ContentType.JSON).body(order).post("/order").then().statusCode(200);

         Thread.sleep(5000);

         String last = MESSAGES.getLast();
         String first = MESSAGES.getFirst();

         assertEquals("CONNECT", first);
         assertThat(last, Matchers.containsString("john.doe@gmail.com"));
      }
   }

   @Test
   void should_receive_dapr_events_correctly() throws DeploymentException, IOException, InterruptedException {
      try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {
         daprClient.publishEvent("pubsub", "topic", new PizzaResource.Event(PizzaResource.EventType.ORDER_PLACED,
               new PizzaResource.Order(new PizzaResource.Customer("John Doe", "john.doe@email.com"),
                     List.of(new PizzaResource.OrderItem(PizzaResource.PizzaType.pepperoni, 1))), "service",
               "Message from QuarkusTest"));

         Thread.sleep(3000);

         String last = MESSAGES.getLast();

         assertThat(last, Matchers.containsString("john.doe@email.com"));
      }
   }

   @Test
   void should_call_delivery_service_when_the_order_is_ready()
         throws DeploymentException, IOException, InterruptedException {
      try (Session session = ContainerProvider.getWebSocketContainer().connectToServer(Client.class, uri)) {

         wiremock.register(WireMock.put("/deliver").willReturn(aResponse().withStatus(200)));

         daprClient.publishEvent("pubsub", "topic", new PizzaResource.Event(PizzaResource.EventType.ORDER_READY,
               new PizzaResource.Order(new PizzaResource.Customer("John Doe", "john.doe@email.com"),
                     List.of(new PizzaResource.OrderItem(PizzaResource.PizzaType.pepperoni, 1))), "service",
               "Message from QuarkusTest"));

         Thread.sleep(3000);

         String last = MESSAGES.getLast();

         assertThat(last, Matchers.containsString("order-out-for-delivery"));
      }
   }

   @ClientEndpoint
   public static class Client {

      @OnOpen
      public void open(Session session) {
         MESSAGES.add("CONNECT");
         // Send a message to indicate that we are ready,
         // as the message handler may not be registered immediately after this callback.
         session.getAsyncRemote().sendText("_ready_");
      }

      @OnMessage
      void message(String msg) {

         MESSAGES.add(msg);
      }
   }
}
