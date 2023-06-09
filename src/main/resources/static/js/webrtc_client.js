'use strict';

// UI 요소 가져옴
const videoButtonOff = document.querySelector('#video_off');
const videoButtonOn = document.querySelector('#video_on');
const audioButtonOff = document.querySelector('#audio_off');
const audioButtonOn = document.querySelector('#audio_on');
const sharingButtonOn = document.querySelector('#share_on');
const sharingButtonOff = document.querySelector('#share_off');
const exitButton = document.querySelector('#exit');
const localRoom = document.querySelector('input#roomId').value;
const localVideo = document.getElementById('local_video');
const remoteVideo = document.getElementById('remote_video');
const localUserName = localStorage.getItem("uuid");
let myName = document.getElementById("userName").value;

// WebRTC STUN 서버
const peerConnectionConfig = {
    'iceServers': [
        {'urls': 'stun:stun.stunprotocol.org:3478'},
        {'urls': 'stun:stun.l.google.com:19302'},
    ]
};

// 화상용
const userConstraints = {
    audio: true,
    video: true
};

// 화면공유용
const displayConstraints = {
    audio: true,
    video: true
};

// WebRTC 변수
let socket
let localStream;
let myPeerConnection;
let videoFlag = true;
let audioFlag = true;
let remoteShareFlag = false;

// 사용자가 말하고있는 상태 감지 코드 S( 구현 완료 안됨 )

const audioContext = new AudioContext();        // AudioContext : 사용자의 PC에서 기본 입력,출력장치로 설정한 하드웨어를 추적하여 이를 제어하는 인터페이스 제공
const analyserNode = audioContext.createAnalyser(); // createAnalyser : AudioContext 객체의 주파수 데이터를 분석에 사용할 수 있는 객체를 생성
analyserNode.fftSize = 2048;

// 입력 장치에 입력된 주파수 정보를 기반으로 평균 볼륨을 계산하여 사용자가 말을 하고 있는 상태인지 점검
function getFrequencyData() {
    // 마이크가 꺼져있을때만 주파수 감지
    if(audioFlag) {
        // create a new array of 8-bit integers (0-255)
        const data = new Uint8Array(analyserNode.frequencyBinCount);

        // populate the array with the frequency data
        analyserNode.getByteFrequencyData(data);

        // 마이크에 감지되는 평균 볼륨 계산
        const averageVolume = data.reduce((acc, val) => acc + val) / data.length;

        // 일정 볼륨 이상으로 마이크 사용중인지 체크
        if (averageVolume > 30) {
            console.log("말하는중!");
        } else {
            console.log("말 안하고있음!");
        }
    }
}

// 50ms 주기로 마이크 사용 정보 체크
setInterval(getFrequencyData, 50);

// 사용자가 말하고있는 상태 감지 코드 E

$(function(){
    start();
    init();
});

function start() {
    // 소켓 연결(wss - web socket ssl) - 서버가 https
    socket = new WebSocket("wss://" + location.host + "/signal");
    socket.binaryType = 'arraybuffer';

    socket.onmessage = function(msg) {
        if(typeof msg.data === "string"){
            // 텍스트 메시지
            let message = JSON.parse(msg.data);
            switch (true) {
                case message.type.includes("Stream"):
                    if(message.from !== localUserName){
                        log('Stream message recieved')
                        handleStreamMessage(message);
                    }
                    break;

                case message.type === "text":
                    if(message.from !== localUserName){
                        log('text message recieved')
                        receiveTextMessage(message.data);
                    }
                    break;

                case message.type === "offer":
                    log('Signal OFFER received');
                    handleOfferMessage(message);
                    break;

                case message.type === "answer":
                    log('Signal ANSWER received');
                    handleAnswerMessage(message);
                    break;

                case message.type === "ice":
                    log('Signal ICE Candidate received');
                    handleNewICECandidateMessage(message);
                    break;

                case message.type === "join":
                    handlePeerConnection();
                    break;

                case message.type === "leave":
                    handlePeerLeave();
                    break;

                default:
                    handleErrorMessage('Wrong type message received from server');
            }
        }else{
            // 바이너리 메시지
            log(msg.data instanceof ArrayBuffer);
            receiveFileMessage(msg.data)
        }

    };

    socket.onopen = function() {
        log('WebSocket connection opened to Room: #' + localRoom);
        sendToServer({
            from: localUserName,
            type: 'join',
            data: localRoom
        });
    };

    setTimeout(() => {
			if (socket.readyState !== 1) {
				log("socket abnormal close. reconnect.");
                start();
			}
		}, 3000);

    socket.onclose = function(event) {
        if (event.code === 1009){
            log(`socket buffer error(code=${event.code})`);
            start();
        }else if (event.code === 1006) {
            log(`socket server error(code=${event.code})`);
            start();
        }else {
            log(`socket close state(code=${event.code})`);
        }
    };

    socket.onerror = function(message) {
        handleErrorMessage("Error: " + message);
    };
}

function stop() {
    log("Send 'leave' message to server");
    sendToServer({
        from: localUserName,
        type: 'leave',
        data: localRoom
    });

    if (myPeerConnection) {
        log('Close the RTCPeerConnection');

        myPeerConnection.onicecandidate = null;
        myPeerConnection.ontrack = null;
        myPeerConnection.onnegotiationneeded = null;

        // 필요할시 사용
        // myPeerConnection.oniceconnectionstatechange = null;
        // myPeerConnection.onsignalingstatechange = null;
        // myPeerConnection.onicegatheringstatechange = null;
        // myPeerConnection.onnotificationneeded = null;
        // myPeerConnection.onremovetrack = null;

        // 비디오 트랙
        if (remoteVideo.srcObject)
            remoteVideo.srcObject.getTracks().forEach(track => track.stop());
        if (localVideo.srcObject)
            localVideo.srcObject.getTracks().forEach(track => track.stop());

        remoteVideo.srcObject = null;
        localVideo.srcObject = null;

        // close the peer connection
        myPeerConnection.close();
        myPeerConnection = null;

        log('Close the socket');
        if (socket != null) {
            socket.close();
        }
    }
}

function log(message) {
    console.log(message);
}

function handleErrorMessage(message) {
    console.error(message);
}

// 처음 join시 로컬 세팅
function handlePeerConnection() {
    createPeerConnection();
    mediaSetting();
}

// rtcPeerConnection 세팅
function createPeerConnection() {
    // rtcPeerConnection 초기화
    myPeerConnection = new RTCPeerConnection(peerConnectionConfig);

    // 이벤트 핸들러 등록
    myPeerConnection.onicecandidate = handleICECandidateEvent;
    myPeerConnection.ontrack = handleTrackEvent;
    myPeerConnection.onnegotiationneeded = handleNegotiationNeededEvent;

    // 필요할시 사용
    // myPeerConnection.onremovetrack = handleRemoveTrackEvent;
    // myPeerConnection.oniceconnectionstatechange = handleICEConnectionStateChangeEvent;
    // myPeerConnection.onicegatheringstatechange = handleICEGatheringStateChangeEvent;
    // myPeerConnection.onsignalingstatechange = handleSignalingStateChangeEvent;
}

// 로컬 미디어 세팅 S
function mediaSetting() {
    // webRtc Stream 관련(https://geoboy.tistory.com/27) // (https://gh402.tistory.com/47) // (https://dreamfuture.tistory.com/60) - 화면 공유
    if(localStream){
        localStream.getTracks().forEach(track => {localStream.removeTrack(track)});
    }

    // 사용자의 카메라/마이크 가져옴
    navigator.mediaDevices.getUserMedia(userConstraints)
        .then(getLocalMediaStream).catch(handleGetUserMediaError);
}

// 자신의 비디오에 mediaStream을 적용 및 rtcPeerConnection에 등록(상대방에게 전달)
function getLocalMediaStream(mediaStream) {
    localStream = mediaStream;
    localVideo.srcObject = mediaStream;
    localStream.getTracks().forEach( track => {
        // 마이크 사용중인지 체크하기 위해 추가 S
        const microphoneSource = audioContext.createMediaStreamSource(mediaStream);
        microphoneSource.connect(analyserNode);
        // 마이크 사용중인지 체크하기 위해 추가 E
        myPeerConnection.addTrack(track, localStream)
    });
}

// 미디어 에러 처리
function handleGetUserMediaError(error) {
    log('navigator.getUserMedia error: ', error);
    switch(error.name) {
        case "NotFoundError":
            alert("Unable to open your call because no camera and/or microphone were found.");
            break;
        case "SecurityError":
        case "PermissionDeniedError":
            // Do nothing; this is the same as the user canceling the call.
            break;
        default:
            alert("Error opening your camera and/or microphone: " + error.message);
            break;
    }
    stop();
}

// 로컬 미디어 세팅 E

// 다른 peer를 찾았을때 offer 전송
function handleNegotiationNeededEvent() {
    myPeerConnection.createOffer().then(function(offer) {
        return myPeerConnection.setLocalDescription(offer);
    })
        .then(function() {
            // offer 전송
            sendToServer({
                from: localUserName,
                type: 'offer',
                sdp: myPeerConnection.localDescription
            });
            log('Negotiation Needed Event: SDP offer sent');
        })
        .catch(function(reason) {
            handleErrorMessage('failure to connect error: ', reason);
        });
}

// offer 메시지 수신시(answer 전송)
function handleOfferMessage(message) {
    log('Accepting Offer Message');
    let desc = new RTCSessionDescription(message.sdp);
    //TODO test this
    if (desc != null && message.sdp != null) {
        log('RTC Signalling state: ' + myPeerConnection.signalingState);
        myPeerConnection.setRemoteDescription(desc).then(function () {
                log("-- Creating answer");
                return myPeerConnection.createAnswer();
            })
            .then(function (answer) {
                log("-- Setting local description after creating answer");
                return myPeerConnection.setLocalDescription(answer);
            })
            .then(function () {
                log("Sending answer packet back to other peer");
                // answer 전송
                sendToServer({
                    from: localUserName,
                    type: 'answer',
                    sdp: myPeerConnection.localDescription
                });
            })
            .catch(handleErrorMessage)
    }
}

// answer 메시지 수신시
function handleAnswerMessage(message) {
    log("The peer has accepted request");
    myPeerConnection.setRemoteDescription(message.sdp).catch(handleErrorMessage);
}

// offer 및 answer가 끝난 후 ice 연결 시작
function handleICECandidateEvent(event) {
    if (event.candidate) {
        sendToServer({
            from: localUserName,
            type: 'ice',
            candidate: event.candidate
        });
        log('ICE Candidate Event: ICE candidate sent');
    }
}

// ice 메시지 수신시
function handleNewICECandidateMessage(message) {
    let candidate = new RTCIceCandidate(message.candidate);
    log("Adding received ICE candidate: " + JSON.stringify(candidate));
    myPeerConnection.addIceCandidate(candidate).catch(handleErrorMessage);
}

// rtcPeerConnection에 트랙이 등록되었을때
function handleTrackEvent(event) {
    log('Track Event: set stream to remote video element');
    remoteVideo.srcObject = event.streams[0];
}

// stream 관련 핸들러
function handleStreamMessage(message) {
    switch (message.type){
        case "videoStreamOn":
            $(remoteVideo).css('display', 'inline');
            break;
        case "videoStreamOff":
            $(remoteVideo).css('display', 'none');
            break;
        case "audioStreamOn":
            remoteVideo.muted = false;
            break;
        case "audioStreamOff":
            remoteVideo.muted = true;
            break;
        case "shareStreamOn":
            remoteShareFlag = true;
            break;
        case "shareStreamOff":
            remoteShareFlag = false;
            break;
    }
}

// 서버로 스켓 메시지 전송
function sendToServer(msg) {
    let msgJSON = JSON.stringify(msg);
    socket.send(msgJSON);
}

/*
 UI 핸들러
  */
// 카메라 off
videoButtonOff.onclick = () => {
    videoFlag = false;
    // localStream.getVideoTracks().forEach(track => {track.stop();localStream.removeTrack(track);});
    $(localVideo).css('display', 'none');
    log('Video Off');
    sendToServer({
        from: localUserName,
        type: 'videoStreamOff',
        data: localRoom
    });
};

// 카메라 on
videoButtonOn.onclick = () => {
    videoFlag = true;
    // localStream.getVideoTracks().forEach(track => {localStream.addTrack(track);});
    $(localVideo).css('display', 'inline');
    log('Video On');
    sendToServer({
        from: localUserName,
        type: 'videoStreamOn',
        data: localRoom
    });
};

// 마이크 음소거
audioButtonOff.onclick = () => {
    audioFlag = false;
    localVideo.muted = true;
    log('Audio Off');
    sendToServer({
        from: localUserName,
        type: 'audioStreamOff',
        data: localRoom
    });
};

// 마이크 킴
audioButtonOn.onclick = () => {
    audioFlag = true;
    localVideo.muted = false;
    log('Audio On');
    sendToServer({
        from: localUserName,
        type: 'audioStreamOn',
        data: localRoom
    });
};

// 화면 공유 활성화
sharingButtonOn.onclick = async () => {
    localStream.getTracks().forEach(track => {
        track.stop();
        localStream.removeTrack(track);
        myPeerConnection.removeTrack(myPeerConnection.getSenders().find(sender => sender.track === track));
    });

    // 화면 공유 스트림 가져옴
    const displayStream = await navigator.mediaDevices.getDisplayMedia(displayConstraints);
    // 사용자의 audio 스트림만 가져옴
    const userStream = await navigator.mediaDevices.getUserMedia({video: false, audio: true});

    // 두개의 스트림을 합침(화면공유 video/audio, 사용자 audio)
    const combinedStream = new MediaStream([
    ...displayStream.getVideoTracks(),
    ...displayStream.getAudioTracks(),
    ...userStream.getAudioTracks()
    ]);

    // 브라우저 공유 중지 버튼을 눌렀을때 화면 공유 off
    combinedStream.getVideoTracks()[0].onended = function () {
        //Jquery trigger
        $(sharingButtonOff).trigger("click");
    }

    localStream = combinedStream;
    localVideo.srcObject = combinedStream;
    localStream.getTracks().forEach(track => {
       myPeerConnection.addTrack(track, localStream);
    });

    // 원래 사용자의 설정대로(video는 무조건 보이도록)
    $(localVideo).css('display', 'inline');
    localVideo.muted = !audioFlag;

    // 상대방에게 화면공유 활성화를 알림
    sendToServer({
        from: localUserName,
        type: 'shareStreamOn',
        data: localRoom
    });
};

// 화면 공유 비활성화
sharingButtonOff.onclick = () => {
    localStream.getTracks().forEach(track => {
        track.stop();
        localStream.removeTrack(track);
        myPeerConnection.removeTrack(myPeerConnection.getSenders().find(sender => sender.track === track));
    });

    // 사용자의 스트림을 가져옴
    navigator.mediaDevices.getUserMedia(userConstraints)
        .then((userStream) => {
            localStream = userStream;
            localVideo.srcObject = userStream;
            localStream.getTracks().forEach(track => {
                myPeerConnection.addTrack(track, localStream);
            });
        }).catch(handleGetUserMediaError);

    // 원래 사용자의 설정대로
    if(videoFlag)
        $(localVideo).css('display', 'inline');
    else
        $(localVideo).css('display', 'none');
    localVideo.muted = !audioFlag;

    // 상대방에게 화면공유 비활성화를 알림
    sendToServer({
        from: localUserName,
        type: 'shareStreamOff',
        data: localRoom
    });
};

// 상대방이 방을 나갔을때경
function handlePeerLeave() {
    remoteVideo.srcObject = null;
}

// 방 나가기
exitButton.onclick = () => {
    stop();
};

////////// 채팅 //////////

// init 함수
function init() {
    // enter 키 이벤트
    $(document).on('keydown', 'div.input-div textarea', function(e){
        if(e.keyCode == 13 && !e.shiftKey) {
            e.preventDefault();
            const text = $(this).val();

            // 메시지 전송
            sendMessage(text);

            // 입력창 clear
            clearTextarea();
            clearFileInput();
        }
    });

    // 파일 첨부하면 바로 전송 div.input-div input[type=file] : 실제 화면에서 사용하는 정보로 수정 필요
    $(document).on('change', 'div.input-div input[type=file]', function(e){
        // 첨부파일 객체
        const file = $('div.input-div input[type=file]').get(0).files[0];

        // 용량 제한 체크
        if (file && file.size > 64 * 1024) { // 64kb 이상인 경우
            alert("64KB 이하의 파일만 첨부 가능합니다.");
            clearFileInput();
            return;
        }

        // 메시지 전송
        sendFile(file);

        // 입력창 clear
        clearTextarea();
        clearFileInput();
    });

    // 파일 드롭다운 이벤트 추가 div.chat_wrap : 실제 화면에서 드롭다운 적용할 영역으로 수정
    $('div.chat_wrap').on('dragenter', function(e) {
        e.stopPropagation();
        e.preventDefault();
        $(this).addClass('dragover');
    });
    $('div.chat_wrap').on('dragover', function(e) {
        e.stopPropagation();
        e.preventDefault();
    });
    $('div.chat_wrap').on('drop', function(e) {
        e.preventDefault();
        $(this).removeClass('dragover');
        const files = e.originalEvent.dataTransfer.files;
        const file = files[0];

        // 용량 제한 체크
        if (file && file.size > 64 * 1024) { // 64kb 이상인 경우
            alert("64KB 이하의 파일만 첨부 가능합니다.");
            return;
        }

        // 메시지 전송
        sendFile(file);
    });
}

// 메세지 태그 생성
function createMessageTag(LR_className, senderName, message, file) {
    // 형식 가져오기
    let chatLi = $('div.chat.format ul li').clone();

    // 값 채우기
    chatLi.addClass(LR_className);
    chatLi.find('.sender span').text(senderName);
    chatLi.find('.message span').html(message);

    if (file) {
        const fileTag = $('<a class="file-attachment"></a>');
        fileTag.attr('href', URL.createObjectURL(file));
        fileTag.attr('download', file.name); // 파일 다운로드 유도
        fileTag.text(file.name + ' [' + (file.size / (1024 * 1024)).toFixed(2) + ' MB]');     // MB 단위로 첨부파일 용량 표시
        chatLi.find('.message').append(fileTag);
    }

    return chatLi;
}

// 메세지 태그 append
function appendMessageTag(LR_className, senderName, message, file) {
    const chatLi = createMessageTag(LR_className, senderName, message, file);

    $('div.chat:not(.format) ul').append(chatLi);

    // 스크롤바 아래 고정
    $('div.chat').scrollTop($('div.chat').prop('scrollHeight'));
}

// 채팅 메세지 전송
function sendMessage(text) {
    // 서버에 전송하는 코드로 후에 대체
    const data = `{"senderName":"${myName}", "message":"${text}"}`;

    // 서버로 전송
    sendToServer({
        from: localUserName,
        type: "text",
        data: data
    });

    appendMessageTag("right", myName, text, null);
}

// 파일 메시지 전송
function sendFile(file) {
    let fileName = file.name;// 파일의 이름
    let reader = new FileReader();
    reader.readAsArrayBuffer(file);// file을 ArrayBuffer로 읽어옴
    let rawData = new ArrayBuffer();// 버퍼사이즈 만큼 데이터를 저장할 rawData 설정

    // 파일을 읽으면 요청되는 이벤트
    reader.onload = function(e){
        rawData = e.target.result;
        log(rawData);
        socket.send(new Uint8Array(rawData));
    }

    appendMessageTag("right", myName, null, file);
}

// 메세지 입력박스 내용 지우기
function clearTextarea() {
    $('div.input-div textarea').val('');
}

// 파일 첨부 입력박스 지우기 div.input-div input[type=file] : 실제 화면에서 사용하는 정보로 수정 필요
function clearFileInput() {
    $('div.input-div input[type=file]').val('');
}

// 메세지 수신
function receiveTextMessage(data) {
    let parsedData = JSON.parse(data);
    const message = parsedData.message.replace(/\n/g, '<br>');
    appendMessageTag("left", parsedData.senderName, message, null);
}

// 파일 수신
function receiveFileMessage(buffer) {
    log(buffer);
    let blob = new Blob([buffer], {type: "application/pdf"});
    let file = new File([blob], "test.pdf");

    appendMessageTag("left", "test", null, file);
}