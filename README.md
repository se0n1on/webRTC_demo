# WebRTC 화상채팅/화면공유/텍스트 채팅 구현(Spring Boot)

### https://github.com/Benkoff/WebRTC-SS 를 참고하여
### 부가적으로 필요한 기능(화면공유 기능, 텍스트 채팅(간단히))을 구현 및 좀 더 안정적으로 사용할 수 있도록 수정한 소스입니다.
### 추가로 간단히 주석을 남겨두어 소스 이해도를 살짝이나마 높여두었습니다.
### * 디자인은 그대로 사용했으며 채팅만 따로 가져와서 붙였고 따로 공을 드리지않았습니다. *
### * 자세한 스펙은 위 깃헙을 참고해주세요 *

### * 참고한 사이트 *
#### - webRTC
#### https://github.com/Benkoff/WebRTC-SS
#### - web 소켓
#### https://shortstories.gitbook.io/studybook/web_socket_c815_b9ac <- 여기 따봉
#### https://technet.tmaxsoft.com/upload/download/online/jeus/pver-20171211-000001/web-engine/chapter_websocket.html
#### https://jungeunpyun.tistory.com/78?category=911250
#### https://scshim.tistory.com/170
#### - 채팅 UI
#### https://dororongju.tistory.com/151
#### - stream 관련
#### https://geoboy.tistory.com/27
#### https://gh402.tistory.com/47
#### - 화면 공유 관련
#### https://dreamfuture.tistory.com/60