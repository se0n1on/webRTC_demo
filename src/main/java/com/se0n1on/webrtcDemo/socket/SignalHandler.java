package com.se0n1on.webrtcDemo.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.se0n1on.webrtcDemo.domain.Room;
import com.se0n1on.webrtcDemo.domain.RoomService;
import com.se0n1on.webrtcDemo.domain.WebSocketMessage;
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
        // 소켓이 닫혔을때
        logger.debug("[ws] Session has been closed with status {}", status);
        final Room room = sessionIdToRoomMap.get(session.getId());

        // 사용자를 방에서 삭제
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

                    // 비디오 on/off, 오디오 on/off, 화면 공유 on/off, 텍스트를 다른 사용자에게 전달
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

                    // 그대로 다른 사용자에게 전달
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

                    // 사용자를 방에 등록
                    Room firstRoom = roomService.findRoomByStringId(data)
                            .orElseThrow(() -> new IOException("Invalid room number received!"));
                    roomService.addClient(firstRoom, userName, session);
                    sessionIdToRoomMap.put(session.getId(), firstRoom);
                    break;

                case "leave":
                    logger.debug("[ws] {} has leaved Room: #{}", userName, data);

                    // 다른 사용자에게 방 나감을 전달
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

                    // 사용자를 방에서 삭제
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

        // 파일을 다른 사용자에게 전달
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
