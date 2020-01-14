# temasys-react-native-webrtc

A WebRTC module for React Native that works with Temasys Skylink Web SDK 2.x.
- Support iOS / Android.
- Support Video / Audio / Data Channels.

**NOTE** for Expo users: this plugin doesn't work unless you eject.

## WebRTC Revision

| temasys-react-native-webrtc | WebRTC Version | arch(ios) | arch(android)  | notes |
| :-------------: | :-------------:| :-----: | :-----: | :-----: |
| master | [M75](https://github.com/jitsi/webrtc/commit/0cd6ce4de669bed94ba47b88cb71b9be0341bb81) | x86_64<br>i386<br>armv7<br>arm64 | armeabi-v7a<br>arm64-v8a<br>x86<br>x86_64 | |

Please see [wiki page](https://github.com/react-native-webrtc/react-native-webrtc/wiki) about revision history.

## Installation

- [iOS](https://github.com/Temasys/temasys-react-native-webrtc-module/blob/master/Documentation/iOSInstallation.md)
- [Android](https://github.com/Temasys/temasys-react-native-webrtc-module/blob/master/Documentation/AndroidInstallation.md)

## Usage
Now, you can use WebRTC like in browser.
In your `index.ios.js`/`index.android.js`, you can require WebRTC to import RTCPeerConnection, RTCSessionDescription, etc.

```javascript
import {
  RTCPeerConnection,
  RTCIceCandidate,
  RTCSessionDescription,
  RTCView,
  MediaStream,
  MediaStreamTrack,
  mediaDevices,
} from 'temasys-react-native-webrtc';
```
Instructions on using this module with Temasys Web SDK can be found [here](https://github.com/Temasys/SkylinkJS).

### RTCView

Rendering of video stream should be implemented the React way.

Rendering RTCView.

```javascript
<RTCView streamURL={this.state.stream.toURL()}/>
```

| Name                           | Type             | Default                   | Description                                                                                                                                |
| ------------------------------ | ---------------- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| mirror                         | boolean          | false               | Indicates whether the video specified by "streamURL" should be mirrored during rendering. Commonly, applications choose to mirror theuser-facing camera.                                                                                                                       |
| objectFit                      | string           | 'contain'           | Can be contain or cover                                                                                                | 
| streamURL                      | string           | ''                  | This is mandatory                                                                                                                      |
| zOrder                         | number           | 0                   | Similarly to zIndex                                                                                              |


### Custom APIs

#### MediaStreamTrack.prototype._switchCamera()

This function allows to switch the front / back cameras in a video track
on the fly, without the need for adding / removing tracks or renegotiating.

#### VideoTrack.enabled

Starting with version 1.67, when setting a local video track's enabled state to
`false`, the camera will be closed, but the track will remain alive. Setting
it back to `true` will re-enable the camera.

## Related projects

### react-native-webrtc

Source: [react-native-webrtc@1.75.3](https://github.com/react-native-webrtc/react-native-webrtc/releases/tag/1.75.3)

## Creator
This repository was originally created by [Wan Huang Yang](https://github.com/oney/).
