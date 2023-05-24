package com.se0n1on.webrtcDemo.vo;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
public class RoomVo {
    private String sid;

    private String uuid;

    private String userName;

    @Builder
    public RoomVo(
            String sid,
            String uuid,
            String userName
    ){
        this.sid = sid;
        this.uuid = uuid;
        this.userName = userName;
    }

    @Builder
    public RoomVo(
            String sid
    ){
        this.sid = sid;
    }
}
