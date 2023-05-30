package com.se0n1on.webrtcDemo.vo;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
public class RoomVo {
   private Long roomId;
    private int concurrentUsers;

    @Builder
    public RoomVo(
            Long roomId,
            int concurrentUsers
    ){
        this.roomId = roomId;
        this.concurrentUsers = concurrentUsers;
    }
}
