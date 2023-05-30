package com.se0n1on.webrtcDemo.service;

import com.se0n1on.webrtcDemo.domain.Room;
import com.se0n1on.webrtcDemo.domain.RoomService;
import com.se0n1on.webrtcDemo.util.Parser;
import com.se0n1on.webrtcDemo.vo.RoomVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Service
public class MainServiceImpl implements MainService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String REDIRECT = "redirect:/";
    private final RoomService roomService;
    private final Parser parser;

    @Autowired
    public MainServiceImpl(final RoomService roomService, final Parser parser) {
        // 임시
        roomService.addRoom(new Room(10L));
        roomService.addRoom(new Room(20L));
        roomService.addRoom(new Room(30L));

        this.roomService = roomService;
        this.parser = parser;
    }

    @Override
    public ModelAndView displayMainPage(final Long roomId, final String uuid) {
        final ModelAndView modelAndView = new ModelAndView("main");
        modelAndView.addObject("id", roomId);
        modelAndView.addObject("rooms", roomService.getRooms());
        modelAndView.addObject("uuid", uuid);
        modelAndView.addObject("msg", "");

        return modelAndView;
    }

    @Override
    public ModelAndView displaySelectedRoom(final String roomId, final String uuid, String userName) {
        // redirect to main page if provided data is invalid
        ModelAndView modelAndView = new ModelAndView(REDIRECT);

        if (parser.parseId(roomId).isPresent()) {
            Room room = roomService.findRoomByStringId(roomId).orElse(null);
            Map<String, WebSocketSession> map = roomService.getClients(room);
            logger.debug("map: " + map.size());
            if(room != null && uuid != null && !uuid.isEmpty()) {
                if(map.size() >= 2){
                    modelAndView = new ModelAndView("main", "roomId", roomId);
                    modelAndView.addObject("rooms", roomService.getRooms());
                    modelAndView.addObject("uuid", uuid);
                    modelAndView.addObject("msg", "The room you selected is already full. Please choose another room");
                }else {
                    logger.debug("User {} is going to join Room #{}", uuid, roomId);
                    modelAndView = new ModelAndView("chat_room", "roomId", roomId);
                    modelAndView.addObject("userName", userName);
                    modelAndView.addObject("uuid", uuid);
                }
            }
        }

        return modelAndView;
    }

    @Override
    public ModelAndView processRoomExit(final String roomId, final String uuid) {
        return new ModelAndView(REDIRECT);
    }

    @Override
    public RoomVo concurrentUserCheck(final String roomId){
        if (parser.parseId(roomId).isPresent()) {
            Room room = roomService.findRoomByStringId(roomId).orElse(null);
            Map<String, WebSocketSession> map = roomService.getClients(room);

            return RoomVo.builder()
                    .roomId(room.getId())
                    .concurrentUsers(map.size())
                    .build();
        }
        return new RoomVo();
    }
}
