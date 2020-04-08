'use strict';

import EventTarget from 'event-target-shim';
import {DeviceEventEmitter, NativeModules} from 'react-native';

import MediaStream from './MediaStream';
import MediaStreamEvent from './MediaStreamEvent';
import MediaStreamTrack from './MediaStreamTrack';
import MediaStreamTrackEvent from './MediaStreamTrackEvent';
import RTCDataChannel from './RTCDataChannel';
import RTCDataChannelEvent from './RTCDataChannelEvent';
import RTCSessionDescription from './RTCSessionDescription';
import RTCIceCandidate from './RTCIceCandidate';
import RTCIceCandidateEvent from './RTCIceCandidateEvent';
import RTCTrackEvent from './RTCTrackEvent';
import RTCEvent from './RTCEvent';
import * as RTCUtil from './RTCUtil';

const {WebRTCModule} = NativeModules;

type RTCSignalingState =
    'stable' |
    'have-local-offer' |
    'have-remote-offer' |
    'have-local-pranswer' |
    'have-remote-pranswer' |
    'closed';

type RTCIceGatheringState =
    'new' |
    'gathering' |
    'complete';

type RTCIceConnectionState =
    'new' |
    'checking' |
    'connected' |
    'completed' |
    'failed' |
    'disconnected' |
    'closed';

const PEER_CONNECTION_EVENTS = [
  'connectionstatechange',
  'icecandidate',
  'icecandidateerror',
  'iceconnectionstatechange',
  'icegatheringstatechange',
  'negotiationneeded',
  'signalingstatechange',
  // Peer-to-peer Data API:
  'datachannel',
  // old:
  'addstream',
  'removestream',
  // new:
  'track',
  // skylink event
  'senderadded'
];

let nextPeerConnectionId = 0;

export default class RTCPeerConnection extends EventTarget(PEER_CONNECTION_EVENTS) {
  localDescription: RTCSessionDescription;
  remoteDescription: RTCSessionDescription;

  signalingState: RTCSignalingState = 'stable';
  iceGatheringState: RTCIceGatheringState = 'new';
  iceConnectionState: RTCIceConnectionState = 'new';

  onconnectionstatechange: ?Function;
  onicecandidate: ?Function;
  onicecandidateerror: ?Function;
  oniceconnectionstatechange: ?Function;
  onicegatheringstatechange: ?Function;
  onnegotiationneeded: ?Function;
  onsignalingstatechange: ?Function;
  ontrack: ?Function;

  onaddstream: ?Function;
  onremovestream: ?Function;

  _peerConnectionId: number;
  _localStreams: Array<MediaStream> = [];
  _localTracks: Array<MediaStreamTrack> = [];
  _remoteStreams: Array<MediaStream> = [];
  _subscriptions: Array<any>;
  _transceivers: Array<any> = [];
  _senders: Array<any> = [];

  /**
   * The RTCDataChannel.id allocator of this RTCPeerConnection.
   */
  _dataChannelIds: Set = new Set();

  constructor(configuration) {
    super();
    this._peerConnectionId = nextPeerConnectionId++;
    if (configuration)
      configuration.sdpSemantics = "unified-plan";
    WebRTCModule.peerConnectionInit(configuration, this._peerConnectionId);
    this._registerEvents();
  }

  addTrack(track: MediaStreamTrack, stream: MediaStream) {
    const streamIndex = this._localStreams.indexOf(stream);
    if (streamIndex === -1) {
      this._localStreams.push(stream);
    }
    const trackIndex = this._localTracks.indexOf(track);
    if (trackIndex !== -1) {
      return;
    }
    WebRTCModule.peerConnectionAddTrack(track.id, stream._reactTag, this._peerConnectionId);
    this._localTracks.push(track);
  }

  removeTrack(sender: RtpSender) {
    let stream;
    let streamIndex;
    let track;
    for (let s = 0; s < this._localStreams.length; s += 1) {
      const tracks = this._localStreams[s].getTracks();
      for (let t = 0; t < tracks.length; t += 1) {
        if (tracks[t].id === sender.track.id) {
          stream = this._localStreams[s];
          streamIndex = s;
          track = tracks[t];
          break;
        }
      }
      break;
    }

    const trackIndex = this._localTracks.indexOf(track);

    if (typeof streamIndex !== 'number' || trackIndex === -1) {
      return;
    }

    this._localStreams.splice(streamIndex, 1);
    this._localTracks.splice(trackIndex, 1);
    WebRTCModule.peerConnectionRemoveTrack(track.id, stream._reactTag, this._peerConnectionId);
  }

  /**
   * Use addTrack
   * @param stream
   * @deprecated
   */
  addStream(stream: MediaStream) {
    const index = this._localStreams.indexOf(stream);
    if (index !== -1) {
      return;
    }
    WebRTCModule.peerConnectionAddStream(stream._reactTag, this._peerConnectionId);
    this._localStreams.push(stream);
  }

  /**
   * Use removeTrack
   * @param stream
   * @deprecated
   */
  removeStream(stream: MediaStream) {
    const index = this._localStreams.indexOf(stream);
    if (index === -1) {
      return;
    }
    this._localStreams.splice(index, 1);
    WebRTCModule.peerConnectionRemoveStream(stream._reactTag, this._peerConnectionId);
  }

  createOffer(options) {
    return new Promise((resolve, reject) => {
      WebRTCModule.peerConnectionCreateOffer(
          this._peerConnectionId,
          RTCUtil.normalizeOfferAnswerOptions(options),
          (successful, data) => {
            if (successful) {
              resolve(new RTCSessionDescription(data));
            } else {
              reject(data); // TODO: convert to NavigatorUserMediaError
            }
          });
    });
  }

  createAnswer(options = {}) {
    return new Promise((resolve, reject) => {
      WebRTCModule.peerConnectionCreateAnswer(
          this._peerConnectionId,
          RTCUtil.normalizeOfferAnswerOptions(options),
          (successful, data) => {
            if (successful) {
              resolve(new RTCSessionDescription(data));
            } else {
              reject(data);
            }
          });
    });
  }

  setConfiguration(configuration) {
    WebRTCModule.peerConnectionSetConfiguration(configuration, this._peerConnectionId);
  }

  setLocalDescription(sessionDescription: RTCSessionDescription) {
    return new Promise((resolve, reject) => {
      WebRTCModule.peerConnectionSetLocalDescription(
          sessionDescription.toJSON ? sessionDescription.toJSON() : sessionDescription,
          this._peerConnectionId,
          (successful, data) => {
            if (successful) {
              this.localDescription = sessionDescription;
              resolve();
            } else {
              reject(data);
            }
          });
    });
  }

  setRemoteDescription(sessionDescription: RTCSessionDescription) {
    return new Promise((resolve, reject) => {
      WebRTCModule.peerConnectionSetRemoteDescription(
          sessionDescription.toJSON ? sessionDescription.toJSON() : sessionDescription,
          this._peerConnectionId,
          (successful, data) => {
            if (successful) {
              this.remoteDescription = sessionDescription;
              resolve();
            } else {
              reject(data);
            }
          });
    });
  }

  addIceCandidate(candidate) {
    return new Promise((resolve, reject) => {
      WebRTCModule.peerConnectionAddICECandidate(
          candidate.toJSON ? candidate.toJSON() : candidate,
          this._peerConnectionId,
          (successful) => {
            if (successful) {
              resolve()
            } else {
              // XXX: This should be OperationError
              reject(new Error('Failed to add ICE candidate'));
            }
          });
    });
  }

  getSenderStats() {
    return this.getStats();
  }

  getStats(track) {
    // NOTE: This returns a Promise but the format of the results is still
    // the "legacy" one. The native side (in Oobj-C) doesn't yet support the
    // new format: https://bugs.chromium.org/p/webrtc/issues/detail?id=6872
    return new Promise((resolve, reject) => {
      WebRTCModule.peerConnectionGetStats(
          (track && track.id) || '',
          this._peerConnectionId,
          (success, data) => {
            if (success) {
              // On both Android and iOS it is faster to construct a single
              // JSON string representing the array of StatsReports and have it
              // pass through the React Native bridge rather than the array of
              // StatsReports. While the implementations do try to be faster in
              // general, the stress is on being faster to pass through the React
              // Native bridge which is a bottleneck that tends to be visible in
              // the UI when there is congestion involving UI-related passing.
              try {
                const stats = JSON.parse(data);
                resolve(stats);
              } catch (e) {
                resolve(null);
                // React Native app will display en error when if the Promise is rejected. Resolve with null object and web sdk will ignore the stats object
                // reject(e);
              }
            } else {
              resolve(null);
              // reject(new Error(data));
            }
          });
    });
  }

  getLocalStreams() {
    return this._localStreams.slice();
  }

  getRemoteStreams() {
    return this._remoteStreams.slice();
  }

  getTransceivers() {
    return this._transceivers;
  }

  getSenders() {
    return this._senders;
  }

  _updateTransceivers(ev) {
    // transceivers list is built from native on every call
    this._transceivers = [];
    for (let i = 0; i < ev.transceivers.length; i += 1) {
      const transceiver = {
        mid: ev.transceivers[i]["transceiverMid"],
        sender: {
          track: null,
        },
        receiver: {
          track: null,
        },
      };
      if (ev.transceivers[i]["senderTrackId"]) {
        transceiver.sender.track = {};
        transceiver.sender.track["id"] = ev.transceivers[i]["senderTrackId"]
      };
      if (ev.transceivers[i]["receiverTrackId"]) {
        transceiver.receiver.track = {};
        transceiver.receiver.track["id"] = ev.transceivers[i]["receiverTrackId"]
      };
      this._transceivers.push(transceiver);
      console.log('_updateTransceivers this._transceivers', this._transceivers);
    }
  }

  close() {
    WebRTCModule.peerConnectionClose(this._peerConnectionId);
  }

  _getTrack(streamReactTag, trackId): MediaStreamTrack {
    const stream
        = this._remoteStreams.find(
        stream => stream._reactTag === streamReactTag);

    return stream && stream._tracks.find(track => track.id === trackId);
  }

  _unregisterEvents(): void {
    this._subscriptions.forEach(e => e.remove());
    this._subscriptions = [];
  }

  _registerEvents(): void {
    this._subscriptions = [
      DeviceEventEmitter.addListener('peerConnectionOnRenegotiationNeeded', ev => {
        if (ev.id !== this._peerConnectionId) {
          return;
        }
        this.dispatchEvent(new RTCEvent('negotiationneeded'));
      }),

      DeviceEventEmitter.addListener('peerConnectionIceConnectionChanged', ev => {
        if (ev.id !== this._peerConnectionId) {
          return;
        }
        this.iceConnectionState = ev.iceConnectionState;
        this.dispatchEvent(new RTCEvent('iceconnectionstatechange'));
        if (ev.iceConnectionState === 'closed') {
          // This PeerConnection is done, clean up event handlers.
          this._unregisterEvents();
        }
      }),

      DeviceEventEmitter.addListener('peerConnectionSignalingStateChanged', ev => {
        if (ev.id !== this._peerConnectionId) {
          return;
        }
        this.signalingState = ev.signalingState;
        this.dispatchEvent(new RTCEvent('signalingstatechange'));
      }),

      DeviceEventEmitter.addListener('peerConnectionUpdatedTransceivers', ev => {
        if (ev.id !== this._peerConnectionId) {
          return;
        }
        if (ev.transceivers && ev.transceivers.length > 0) {
          this._updateTransceivers(ev);
        }
      }),

      DeviceEventEmitter.addListener('peerConnectionAddTrack', ev => {
        if (ev.id !== this._peerConnectionId) {
          return;
        }
        if  (!ev.transceiverMid) {
          throw new Error('transceiverMid is undefined');
        }
        ev.streams.forEach((s)=> {
          const stream = new MediaStream(s);
          let track = null;
          stream.getTracks().forEach((t) => {
            if  (!track) {
              track = t;
            } else {
              throw new Error('Stream cannot have more than 1 track');
            }
          })
          this.dispatchEvent(new RTCTrackEvent ('track', { transceiver: { mid: ev.transceiverMid }, track, streams: [stream]}));
          this._remoteStreams.push(stream);
        })
        // android passes transceivers in peerConnectionAddTrack while iOS passes transceivers in peerConnectionUpdatedTransceivers
        if (ev.transceivers && ev.transceivers.length > 0) {
          this._updateTransceivers(ev);
        }
      }),

      DeviceEventEmitter.addListener('peerConnectionAddedSender', function(ev) {
        if (ev.id !== this._peerConnectionId) {
          return;
        }
        const sender = {
          track: null,
          replaceTrack: null,
          getStats: this.getSenderStats.bind(this)
        }
        /*TODO:
         build sender object
         sender.track.id
         sender.getStats()
         sender.replaceTrack() --- setTrack() in native
         */
        console.log("peerConnectionAddedSender: ", ev);
        for  (let t = 0; t < this._localTracks.length; t += 1) {
          if  (this._localTracks[t].id === ev.senderId || this._localTracks[t].id === ev.senderTrackId) {
            sender.track = this._localTracks[t];
            break;
          }
        }

        if (!sender.track) {
          throw new Error('Sender track is null');
        }

        this._senders.push(sender);
        this.dispatchEvent(new RTCEvent('senderadded', { sender }));
      }.bind(this)),

      DeviceEventEmitter.addListener('peerConnectionRemovedSender', (ev) => {
        if (ev.id !== this._peerConnectionId) {
          return;
        }

        console.log("peerConnectionRemovedSender: ", ev);
        for  (let s = 0; s < this._senders.length; s += 1) {
          if  (this._senders[s].id === ev.senderId || this._senders[s].id === ev.senderTrackId) {
            this._senders.splice(s, 1);
            break;
          }
        }
      }),

      DeviceEventEmitter.addListener('peerConnectionAddedStream', ev => {
        if (ev.id !== this._peerConnectionId) {
          return;
        }
        const stream = new MediaStream(ev);
        this.dispatchEvent(new MediaStreamEvent('addstream', {stream}));
      }),
      DeviceEventEmitter.addListener('peerConnectionRemovedStream', ev => {
        if (ev.id !== this._peerConnectionId) {
          return;
        }
        const stream = this._remoteStreams.find(s => s._reactTag === ev.streamId);
        if (stream) {
          const index = this._remoteStreams.indexOf(stream);
          if (index !== -1) {
            this._remoteStreams.splice(index, 1);
          }
        }
        this.dispatchEvent(new MediaStreamEvent('removestream', {stream}));
      }),
      DeviceEventEmitter.addListener('mediaStreamTrackMuteChanged', ev => {
        if (ev.peerConnectionId !== this._peerConnectionId) {
          return;
        }
        const track = this._getTrack(ev.streamReactTag, ev.trackId);
        if (track) {
          track.muted = ev.muted;
          const eventName = ev.muted ? 'mute' : 'unmute';
          track.dispatchEvent(new MediaStreamTrackEvent(eventName, {track}));
        }
      }),
      DeviceEventEmitter.addListener('peerConnectionGotICECandidate', ev => {
        if (ev.id !== this._peerConnectionId) {
          return;
        }
        const candidate = new RTCIceCandidate(ev.candidate);
        const event = new RTCIceCandidateEvent('icecandidate', {candidate});
        this.dispatchEvent(event);
      }),
      DeviceEventEmitter.addListener('peerConnectionIceGatheringChanged', ev => {
        if (ev.id !== this._peerConnectionId) {
          return;
        }
        this.iceGatheringState = ev.iceGatheringState;

        if (this.iceGatheringState === 'complete') {
          this.dispatchEvent(new RTCIceCandidateEvent('icecandidate', null));
        }

        this.dispatchEvent(new RTCEvent('icegatheringstatechange'));
      }),
      DeviceEventEmitter.addListener('peerConnectionDidOpenDataChannel', ev => {
        if (ev.id !== this._peerConnectionId) {
          return;
        }
        const evDataChannel = ev.dataChannel;
        const id = evDataChannel.id;
        // XXX RTP data channels are not defined by the WebRTC standard, have
        // been deprecated in Chromium, and Google have decided (in 2015) to no
        // longer support them (in the face of multiple reported issues of
        // breakages).
        if (typeof id !== 'number' || id === -1) {
          return;
        }
        const channel
            = new RTCDataChannel(
            this._peerConnectionId,
            evDataChannel.label,
            evDataChannel);
        // XXX webrtc::PeerConnection checked that id was not in use in its own
        // SID allocator before it invoked us. Additionally, its own SID
        // allocator is the authority on ResourceInUse. Consequently, it is
        // (pretty) safe to update our RTCDataChannel.id allocator without
        // checking for ResourceInUse.
        this._dataChannelIds.add(id);
        this.dispatchEvent(new RTCDataChannelEvent('datachannel', {channel}));
      })
    ];
  }

  /**
   * Creates a new RTCDataChannel object with the given label. The
   * RTCDataChannelInit dictionary can be used to configure properties of the
   * underlying channel such as data reliability.
   *
   * @param {string} label - the value with which the label attribute of the new
   * instance is to be initialized
   * @param {RTCDataChannelInit} dataChannelDict - an optional dictionary of
   * values with which to initialize corresponding attributes of the new
   * instance such as id
   */
  createDataChannel(label: string, dataChannelDict?: ?RTCDataChannelInit) {
  let id;
  const dataChannelIds = this._dataChannelIds;
  if (dataChannelDict && 'id' in dataChannelDict) {
  id = dataChannelDict.id;
  if (typeof id !== 'number') {
  throw new TypeError('DataChannel id must be a number: ' + id);
  }
  if (dataChannelIds.has(id)) {
    throw new ResourceInUse('DataChannel id already in use: ' + id);
  }
  } else {
    // Allocate a new id.
    // TODO Remembering the last used/allocated id and then incrementing it to
    // generate the next id to use will surely be faster. However, I want to
    // reuse ids (in the future) as the RTCDataChannel.id space is limited to
    // unsigned short by the standard:
    // https://www.w3.org/TR/webrtc/#dom-datachannel-id. Additionally, 65535
    // is reserved due to SCTP INIT and INIT-ACK chunks only allowing a
    // maximum of 65535 streams to be negotiated (as defined by the WebRTC
    // Data Channel Establishment Protocol).
    for (id = 0; id < 65535 && dataChannelIds.has(id); ++id);
    // TODO Throw an error if no unused id is available.
    dataChannelDict = Object.assign({id}, dataChannelDict);
  }
  // Skylink creates a test dataChannel to obtain data channel information which can be filled by just returning an instance of RTCDataChannel without calling native code
  if (label !== 'test'){
    WebRTCModule.createDataChannel(
        this._peerConnectionId,
        label,
        dataChannelDict);
    dataChannelIds.add(id);
  }
  return new RTCDataChannel(this._peerConnectionId, label, dataChannelDict);
  }
}
