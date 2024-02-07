package io.diagrid.dapr;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.diagrid.dapr.PizzaResource.Order;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

@Path("/deliver")
@RegisterRestClient(configKey = "delivery-service")
public interface DeliveryRestClient {

    @PUT
    @ClientHeaderParam(name = "Content-Type", value = "application/json")
    @ClientHeaderParam(name = "dapr-app-id", value = "delivery-service")
    void call(Order order);
}
