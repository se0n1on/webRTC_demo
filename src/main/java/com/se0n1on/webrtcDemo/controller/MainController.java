package com.se0n1on.webrtcDemo.controller;

import com.se0n1on.webrtcDemo.service.MainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import java.util.UUID;

@Controller
@ControllerAdvice
public class MainController {    
    @Autowired private MainService mainService;

    @GetMapping({"", "/", "/index", "/home", "/main"})
    public ModelAndView displayMainPage(final Long id, final String uuid) {
        return this.mainService.displayMainPage(id, uuid);
    }

    @GetMapping("/room/{roomId}")
    public ModelAndView displaySelectedRoom(@PathVariable("roomId") final String roomId) {
        String uuid = UUID.randomUUID().toString();
        return this.mainService.displaySelectedRoom(roomId, uuid, "name");
    }

    @GetMapping("/room/{roomId}/user/{uuid}/exit")
    public ModelAndView processRoomExit(@PathVariable("roomId") final String roomId, @PathVariable("uuid") final String uuid) {
        return this.mainService.processRoomExit(roomId, uuid);
    }
}
