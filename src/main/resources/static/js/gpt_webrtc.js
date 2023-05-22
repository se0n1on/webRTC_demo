'use strict';

const video = document.querySelector('video'); // 비디오 엘리먼트를 가져옵니다.
const startButton = document.querySelector('button#start'); // 시작 버튼 엘리먼트를 가져옵니다.
const callButton = document.querySelector('button#call'); // 통화 버튼 엘리먼트를 가져옵니다.
const hangupButton = document.querySelector('button#hangup'); // 통화 종료 버튼 엘리먼트를 가져옵니다.
const screenshareButton = document.querySelector('button#screenshare'); // 화면 공유 버튼 엘리먼트를 가져옵니다.

startButton.onclick = start; // 시작 버튼 클릭 이벤트에 start 함수를 연결합니다.
callButton.onclick = call; // 통화 버튼 클릭 이벤트에 call 함수를 연결합니다.
hangupButton.onclick = hangup; // 통화 종료 버튼 클릭 이벤트에 hangup 함수를 연결합니다.
screenshareButton.onclick = screenshare; // 화면 공유 버튼 클릭 이벤트에 screenshare 함수를 연결합니다.

let startTime;
let localStream;
let pc1;
let pc2;

// 사용자의 미디어(비디오, 오디오)를 가져옵니다.
function start() {
  startButton.disabled = true;
  navigator.mediaDevices.getUserMedia({
    audio: true,
    video: true
  })
  .then(gotStream)
  .catch(e => alert('getUserMedia() error: ' + e.name));
}

// 두 피어를 연결하고, ICE candidate를 교환합니다.
function call() {
  callButton.disabled = true;
  hangupButton.disabled = false;
  startTime = window.performance.now();
  const videoTracks = localStream.getVideoTracks();
  const audioTracks = localStream.getAudioTracks();
  pc1 = new RTCPeerConnection();
  pc2 = new RTCPeerConnection();
  pc1.onicecandidate = e => pc2.addIceCandidate(e.candidate);
  pc2.onicecandidate = e => pc1.addIceCandidate(e.candidate);
  pc2.ontrack = gotRemoteStream;
  localStream.getTracks().forEach(track => pc1.addTrack(track, localStream));
  pc1.createOffer()
    .then(gotDescription1)
    .catch(onCreateSessionDescriptionError);
}

// 세션 생성에 실패했을 때의 에러 핸들링 함수
function onCreateSessionDescriptionError(error) {
  console.log(`Failed to create session description: ${error.toString()}`);
}

// 로컬 스트림을 받았을 때의 핸들러 함수
function gotStream(stream) {
  callButton.disabled = false;
  localStream = stream;
  video.srcObject = stream;
}

// pc1에서 offer를 만들고, 이를 pc2에게 전달합니다.
function gotDescription1(desc) {
  pc1.setLocalDescription(desc);
  pc2.setRemoteDescription(desc);
  pc2.createAnswer().then(
    gotDescription2,
    onCreateSessionDescriptionError
  );
}

// pc2에서 answer를 만들고, 이를 pc1에게 전달합니다.
function gotDescription2(desc) {
  pc2.setLocalDescription(desc);
  pc1.setRemoteDescription(desc);
}

// 통화를 종료하고, 관련 리소스를 해제합니다.
function hangup() {
  pc1.close();
  pc2.close();
  pc1 = null;
  pc2 = null;
  hangupButton.disabled = true;
  callButton.disabled = false;
}

// 원격 스트림을 받았을 때의 핸들러 함수
function gotRemoteStream(e) {
  if (video.srcObject !== e.streams[0]) {
    video.srcObject = e.streams[0];
  }
}

// 화면 공유 기능을 시작합니다.
function screenshare() {
  navigator.mediaDevices.getDisplayMedia({
    video: {
      cursor: 'always'
    },
    audio: false
  }).then(screenStream => {
    let screenTrack = screenStream.getTracks()[0];
    let sender = pc1.getSenders().find(s => s.track.kind == screenTrack.kind);
    sender.replaceTrack(screenTrack);
  });
}