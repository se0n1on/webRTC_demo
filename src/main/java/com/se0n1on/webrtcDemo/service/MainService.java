package com.se0n1on.webrtcDemo.service;

import com.se0n1on.webrtcDemo.vo.RoomVo;
import org.springframework.web.servlet.ModelAndView;

public interface MainService {
    ModelAndView displayMainPage(Long id, String uuid);
    ModelAndView displaySelectedRoom(String sid, String uuid, String userName);
    ModelAndView processRoomExit(String sid, String uuid);

    RoomVo concurrentUserCheck(String roomID);
}
