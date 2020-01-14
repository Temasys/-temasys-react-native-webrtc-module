'use strict';
import type MediaStreamTrack from './MediaStreamTrack';
import type MediaStream from './MediaStream';
export default class RTCTrackEvent {
  type: string;
  track: MediaStreamTrack;
  streams: Array<MediaStream>;
  constructor(type, eventInitDict) {
    this.type = type.toString();
    Object.assign(this, eventInitDict);
  }
}