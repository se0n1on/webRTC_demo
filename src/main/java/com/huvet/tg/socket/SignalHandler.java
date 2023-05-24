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

    // session id to room mapping
    private Map<String, Room> sessionIdToRoomMap = new HashMap<>();

    // message types, used in signalling:
    // text message
    private static final String MSG_TYPE_TEXT = "text";
    // file message
    private static final String MSG_TYPE_FILE = "file";
    // SDP Offer message
    private static final String MSG_TYPE_OFFER = "offer";
    // SDP Answer message
    private static final String MSG_TYPE_ANSWER = "answer";
    // New ICE Candidate message
    private static final String MSG_TYPE_ICE = "ice";
    // join room data message
    private static final String MSG_TYPE_JOIN = "join";
    // leave room data message
    private static final String MSG_TYPE_LEAVE = "leave";

    private static final String MSG_TYPE_VIDEO_STREAM_ON = "videoStreamOn";

    private static final String MSG_TYPE_VIDEO_STREAM_OFF = "videoStreamOff";

    private static final String MSG_TYPE_AUDIO_STREAM_ON = "audioStreamOn";

    private static final String MSG_TYPE_AUDIO_STREAM_OFF = "audioStreamOff";

    private static final String MSG_TYPE_SHARE_STREAM_ON = "shareStreamOn";

    private static final String MSG_TYPE_SHARE_STREAM_OFF = "shareStreamOff";

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
        // webSocket has been opened, send a message to the client
        // when data field contains 'true' value, the client starts negotiating
        // to establish peer-to-peer connection, otherwise they wait for a counterpart
        sendMessage(session, new WebSocketMessage("Server", MSG_TYPE_JOIN, null, null, null));
    }

    @Override
    protected void handleTextMessage(final WebSocketSession session, final TextMessage textMessage) {
        // a message has been received
        try {
            WebSocketMessage message = objectMapper.readValue(textMessage.getPayload(), WebSocketMessage.class);
            String userName = message.getFrom(); // origin of the message
            String data = message.getData(); // payload
            final Room room = sessionIdToRoomMap.get(session.getId());
            switch (message.getType()) {
                // stream status
                case MSG_TYPE_VIDEO_STREAM_ON: case MSG_TYPE_VIDEO_STREAM_OFF: case MSG_TYPE_AUDIO_STREAM_ON: case MSG_TYPE_AUDIO_STREAM_OFF: case MSG_TYPE_SHARE_STREAM_ON: case MSG_TYPE_SHARE_STREAM_OFF: case MSG_TYPE_TEXT: case MSG_TYPE_FILE:
                    logger.debug("[ws] Stream or Text or File: {}",data);
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

                case MSG_TYPE_OFFER: case MSG_TYPE_ANSWER: case MSG_TYPE_ICE:
                    logger.debug("[ws] type {} message received", message.getType());
                    Object candidate = message.getCandidate();
                    Object sdp = message.getSdp();
                    if (room != null) {
                        Map<String, WebSocketSession> clients = roomService.getClients(room);
                        for(Map.Entry<String, WebSocketSession> client : clients.entrySet())  {
                            if(!client.getValue().getId().equals(session.getId())) {
                                sendMessage(client.getValue(),
                                        new WebSocketMessage(
                                                userName,
                                                message.getType(),
                                                data,
                                                candidate,
                                                sdp));
                            }
                        }
                    }
                    break;

                // identify user and their opponent
                case MSG_TYPE_JOIN:
                    // message.data contains connected room id
                    logger.debug("[ws] {} has joined Room: #{}", userName, data);
                    Room firstRoom = roomService.findRoomByStringId(data)
                            .orElseThrow(() -> new IOException("Invalid room number received!"));
                    // add client to the Room clients list
                    roomService.addClient(firstRoom, userName, session);
                    sessionIdToRoomMap.put(session.getId(), firstRoom);
                    break;

                case MSG_TYPE_LEAVE:
                    logger.debug("[ws] {} is going to leave Room: #{}", userName, data);
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

                // something should be wrong with the received message, since it's type is unrecognizable
                default:
                    logger.debug("[ws] Type of the received message {} is undefined!", message.getType());
                    // handle this if needed
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
