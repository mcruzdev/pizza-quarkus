package io.diagrid.dapr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.websocket.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Encoder;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/ws")
@ApplicationScoped
public class Websocket {

    static final Logger LOGGER = LoggerFactory.getLogger(Websocket.class);
    public static Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        LOGGER.info("Session started");
        SESSIONS.put("default", session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        SESSIONS.remove("default");
        LOGGER.error("User default left on error: " + throwable);
    }
}
