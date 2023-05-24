package com.huvet.tg.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvet.tg.domain.Room;
import com.huvet.tg.domain.RoomService;
import com.huvet.tg.domain.WebSocketMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;

@Component
public class SignalHandler extends TextWebSocketHandler {

    @Autowired private RoomService roomService;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, Room> sessionIdToRoomMap = new HashMap<>();

    @Override
    public void afterConnectionClosed(final WebSocketSession session, final CloseStatus status) {
        logger.debug("[ws] Session has been closed with status {}", status);
        final Room room = sessionIdToRoomMap.get(session.getId());
        Optional<String> client = roomService.getClients(room).entrySet().stream()
            .filter(entry -> Objects.equals(entry.getValue().getId(), session.getId()))
            .map(Map.Entry::getKey)
            .findAny();
        client.ifPresent(c -> roomService.removeClientByName(room, c));
        sessionIdToRoomMap.remove(session.getId());
    }

    @Override
    public void afterConnectionEstablished(final WebSocketSession session) {
        sendMessage(session, new WebSocketMessage("Server", "join", null, null, null));
    }

    @Override
    protected void handleTextMessage(final WebSocketSession session, final TextMessage textMessage) {
        // a message has been received
        try {
            WebSocketMessage message = objectMapper.readValue(textMessage.getPayload(), WebSocketMessage.class);
            String userName = message.getFrom();
            String data = message.getData();
            final Room room = sessionIdToRoomMap.get(session.getId());
            switch (message.getType()) {

                case "videoStreamOn": case "videoStreamOff": case "audioStreamOn": case "audioStreamOff": case "shareStreamOn": case "shareStreamOff": case "text":
                    logger.debug("[ws] Stream or Text: {}",data);
                    if (room != null) {
                        Map<String, WebSocketSession> clients = roomService.getClients(room);
                        for(Map.Entry<String, WebSocketSession> client : clients.entrySet()) {
                            if(!client.getValue().getId().equals(session.getId())) {
                                sendMessage(client.getValue(),
                                        new WebSocketMessage(
                                                userName,
                                                message.getType(),
                                                data,
                                                message.getCandidate(),
                                                null));
                            }
                        }
                    }
                    break;

                case "offer": case "answer": case "ice":
                    logger.debug("[ws] type {} message received", message.getType());
                    if (room != null) {
                        Map<String, WebSocketSession> clients = roomService.getClients(room);
                        for(Map.Entry<String, WebSocketSession> client : clients.entrySet())  {
                            if(!client.getValue().getId().equals(session.getId())) {
                                sendMessage(client.getValue(),
                                        new WebSocketMessage(
                                                userName,
                                                message.getType(),
                                                data,
                                                message.getCandidate(),
                                                message.getSdp()));
                            }
                        }
                    }
                    break;

                case "join":
                    logger.debug("[ws] {} has joined Room: #{}", userName, data);
                    Room firstRoom = roomService.findRoomByStringId(data)
                            .orElseThrow(() -> new IOException("Invalid room number received!"));
                    roomService.addClient(firstRoom, userName, session);
                    sessionIdToRoomMap.put(session.getId(), firstRoom);
                    break;

                case "leave":
                    logger.debug("[ws] {} has leaved Room: #{}", userName, data);
                    if (room != null) {
                        Map<String, WebSocketSession> clients = roomService.getClients(room);
                        for(Map.Entry<String, WebSocketSession> client : clients.entrySet()) {
                                 if(!client.getValue().getId().equals(session.getId())) {
                                     sendMessage(client.getValue(),
                                             new WebSocketMessage(
                                                     userName,
                                                     message.getType(),
                                                     data,
                                                     message.getCandidate(),
                                                     null));
                                 }
                            }
                    }

                    Optional<String> client = roomService.getClients(room).entrySet().stream()
                            .filter(entry -> Objects.equals(entry.getValue().getId(), session.getId()))
                            .map(Map.Entry::getKey)
                            .findAny();
                    client.ifPresent(c -> roomService.removeClientByName(room, c));
                    sessionIdToRoomMap.remove(session.getId());
                    break;

                default:
                    logger.debug("Wrong type message received from server");
            }

        } catch (IOException e) {
            logger.debug("An error occured: {}", e.getMessage());
        }
    }

    @Override
    protected void handleBinaryMessage(final WebSocketSession session, final BinaryMessage message){
        final Room room = sessionIdToRoomMap.get(session.getId());
        try {
            if (room != null) {
                Map<String, WebSocketSession> clients = roomService.getClients(room);
                for(Map.Entry<String, WebSocketSession> client : clients.entrySet()) {
                        if(!client.getValue().getId().equals(session.getId()))
                            client.getValue().sendMessage(new BinaryMessage(message.getPayload().array()));
                    }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            logger.debug("An error occured: {}", e.getMessage());
        }
    }
}
