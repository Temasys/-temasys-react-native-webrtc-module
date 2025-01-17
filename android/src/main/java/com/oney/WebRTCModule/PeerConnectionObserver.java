package com.oney.WebRTCModule;

import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.VideoTrack;
import org.webrtc.RtpTransceiver;

class PeerConnectionObserver implements PeerConnection.Observer {
    private final static String TAG = WebRTCModule.TAG;

    private final SparseArray<DataChannel> dataChannels
        = new SparseArray<DataChannel>();
    private final int id;
    private PeerConnection peerConnection;
    final List<MediaStream> localStreams;
    final List<MediaStreamTrack> localTracks;
    final Map<String, MediaStream> remoteStreams;
    final Map<String, MediaStreamTrack> remoteTracks;
    final Map<String, RtpSender> senders;
    private final VideoTrackAdapter videoTrackAdapters;
    private final WebRTCModule webRTCModule;

    /**
     * The <tt>StringBuilder</tt> cache utilized by {@link #statsToJSON} in
     * order to minimize the number of allocations of <tt>StringBuilder</tt>
     * instances and, more importantly, the allocations of its <tt>char</tt>
     * buffer in an attempt to improve performance.
     */
    private SoftReference<StringBuilder> statsToJSONStringBuilder
        = new SoftReference<>(null);

    PeerConnectionObserver(WebRTCModule webRTCModule, int id) {
        this.webRTCModule = webRTCModule;
        this.id = id;
        this.localStreams = new ArrayList<MediaStream>();
        this.localTracks = new ArrayList<MediaStreamTrack>();
        this.remoteStreams = new HashMap<String, MediaStream>();
        this.remoteTracks = new HashMap<String, MediaStreamTrack>();
        this.senders = new HashMap<String, RtpSender>();
        this.videoTrackAdapters = new VideoTrackAdapter(webRTCModule, id);
    }

    /**
     * Adds a specific local <tt>MediaStream</tt> to the associated
     * <tt>PeerConnection</tt>.
     *
     * @param localStream the local <tt>MediaStream</tt> to add to the
     * associated <tt>PeerConnection</tt>
     * @return <tt>true</tt> if the specified <tt>localStream</tt> was added to
     * the associated <tt>PeerConnection</tt>; otherwise, <tt>false</tt>
     * @deprecated
     */
    boolean addStream(MediaStream localStream) {
        if (peerConnection != null && peerConnection.addStream(localStream)) {
            localStreams.add(localStream);

            return true;
        }

        return false;
    }

    /**
     * Adds a specific local <tt>MediaStreamTrack</tt> to the associated
     * <tt>PeerConnection</tt>.
     *
     * @param localTrack the local <tt>MediaStreamTrack</tt> to add to the
     * associated <tt>PeerConnection</tt>
     * @return <tt>true</tt> if the specified <tt>localTrack</tt> was added to
     * the associated <tt>PeerConnection</tt>; otherwise, <tt>false</tt>
     */
    boolean addTrack(MediaStreamTrack localTrack, MediaStream localStream) {
        if (peerConnection != null) {
            Log.d(TAG, "START pc.addTrack ------------------- " + "peerConnection ID: " + id);
            // save to local
            localStreams.add(localStream);
            localTracks.add(localTrack);
            String trackId = localTrack.id();
            Log.d(TAG, "track ID: " + trackId);

            String streamId = localStream.getId();
            List<String> streamIds = new ArrayList<String>();
            streamIds.add(streamId);
            RtpSender sender = peerConnection.addTrack(localTrack, streamIds);

            WritableMap params = Arguments.createMap();
            String senderId = sender.id();
            MediaStreamTrack senderTrack = sender.track();
            String senderTrackId = senderTrack.id();
            params.putInt("id", id);
            params.putString("senderId", senderId); // most of the time senderId is trackId
            params.putString("senderTrackId", senderTrackId); // check senderTrackId when senderId is not the trackId
            webRTCModule.sendEvent("peerConnectionAddedSender", params);

            senders.put(trackId, sender);
            Log.d(TAG, "Created Sender ID: " + senderId);
            Log.d(TAG, "Created Sender Track ID: " + senderTrackId);
            Log.d(TAG, "Current localTracks: " + localTracks);

            Log.d(TAG, "END pc.addTrack -------------------");

            return true;
        }

        return false;
    }

    /**
     * Removes a track from the associated <tt>PeerConnection</tt>.
     * @param localTrack the local <tt>MediaStreamTrack</tt> to remove from the
     * associated <tt>PeerConnection</tt>
     * @return <tt>true</tt> if the specified <tt>localTrack</tt> was removed from
     * the associated <tt>PeerConnection</tt>; otherwise, <tt>false</tt>
     */
    boolean removeTrack(MediaStreamTrack localTrack) {
        if (peerConnection != null) {
            Log.d(TAG, "START pc.removeTrack ------------------- " + "peerConnection ID: " + id);
            Log.d(TAG, "Logging transceivers before removeTrack");
            getTransceivers();

            String trackId = new String(localTrack.id());
            RtpSender sender = senders.get(trackId);
            String senderId = new String(sender.id());

            WritableMap params = Arguments.createMap();
            MediaStreamTrack senderTrack = sender.track();
            String senderTrackId = senderTrack.id();
            params.putInt("id", id);
            params.putString("senderId", senderId); // most of the time senderId is trackId
            params.putString("senderTrackId", senderTrackId); // check senderTrackId when senderId is not the trackId
            webRTCModule.sendEvent("peerConnectionRemovedSender", params);
            senders.remove(trackId);

            Log.d(TAG, "Track to delete ID: " + trackId);
            Log.d(TAG, "Sender to remove ID: " + senderId);
            Log.d(TAG, "Removed sender: " + peerConnection.removeTrack(sender));

        }

        Log.d(TAG, "Logging transceivers after removeTrack");
        getTransceivers();

        Log.d(TAG, "END pc.removeTrack -------------------");

        return localTracks.remove(localTrack);
    }

    /**
     * Removes a specific local <tt>MediaStream</tt> from the associated
     * <tt>PeerConnection</tt>.
     *
     * @param localStream the local <tt>MediaStream</tt> from the associated
     * <tt>PeerConnection</tt>
     * @return <tt>true</tt> if removing the specified <tt>mediaStream</tt> from
     * this instance resulted in a modification of its internal list of local
     * <tt>MediaStream</tt>s; otherwise, <tt>false</tt>
     * @deprecated
     */
    boolean removeStream(MediaStream localStream) {
        if (peerConnection != null) {
            peerConnection.removeStream(localStream);
        }

        return localStreams.remove(localStream);
    }

    PeerConnection getPeerConnection() {
        return peerConnection;
    }

    void setPeerConnection(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }

    void close() {
        Log.d(TAG, "START pc.close ------------------- " + "peerConnection ID: " + id);
        // Remove video track adapters
        for (MediaStream stream : remoteStreams.values()) {
            for (VideoTrack videoTrack : stream.videoTracks) {
                videoTrackAdapters.removeAdapter(videoTrack);
            }
        }

        //Call dispose() to free all remaining resources held
        // by the PeerConnection instance (RtpReceivers, RtpSenders, etc.)
        peerConnection.dispose();
        localTracks.clear();
        localStreams.clear();
        remoteStreams.clear();
        remoteTracks.clear();
        senders.clear();

        // Unlike on iOS, we cannot unregister the DataChannel.Observer
        // instance on Android. At least do whatever else we do on iOS.
        dataChannels.clear();
        Log.d(TAG, "END pc.close -------------------");
    }

    void createDataChannel(String label, ReadableMap config) {
        DataChannel.Init init = new DataChannel.Init();
        if (config != null) {
            if (config.hasKey("id")) {
                init.id = config.getInt("id");
            }
            if (config.hasKey("ordered")) {
                init.ordered = config.getBoolean("ordered");
            }
            if (config.hasKey("maxRetransmitTime")) {
                init.maxRetransmitTimeMs = config.getInt("maxRetransmitTime");
            }
            if (config.hasKey("maxRetransmits")) {
                init.maxRetransmits = config.getInt("maxRetransmits");
            }
            if (config.hasKey("protocol")) {
                init.protocol = config.getString("protocol");
            }
            if (config.hasKey("negotiated")) {
                init.negotiated = config.getBoolean("negotiated");
            }
        }
        DataChannel dataChannel = peerConnection.createDataChannel(label, init);
        int dataChannelId = init.id;
        if (-1 != dataChannelId) {
            dataChannels.put(dataChannelId, dataChannel);
            registerDataChannelObserver(dataChannelId, dataChannel);
        }
    }

    void dataChannelClose(int dataChannelId) {
        DataChannel dataChannel = dataChannels.get(dataChannelId);
        if (dataChannel != null) {
            dataChannel.close();
            dataChannels.remove(dataChannelId);
        } else {
            Log.d(TAG, "dataChannelClose() dataChannel is null");
        }
    }

    void dataChannelSend(int dataChannelId, String data, String type) {
        DataChannel dataChannel = dataChannels.get(dataChannelId);
        if (dataChannel != null) {
            byte[] byteArray;
            if (type.equals("text")) {
                try {
                    byteArray = data.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.d(TAG, "Could not encode text string as UTF-8.");
                    return;
                }
            } else if (type.equals("binary")) {
                byteArray = Base64.decode(data, Base64.NO_WRAP);
            } else {
                Log.e(TAG, "Unsupported data type: " + type);
                return;
            }
            ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
            DataChannel.Buffer buffer = new DataChannel.Buffer(byteBuffer, type.equals("binary"));
            dataChannel.send(buffer);
        } else {
            Log.d(TAG, "dataChannelSend() dataChannel is null");
        }
    }

    @SuppressWarnings("deprecation") // TODO(saghul): getStats is deprecated.
    void getStats(String trackId, final Callback cb) {
        MediaStreamTrack track = null;
        if (trackId == null
                || trackId.isEmpty()
                || (track = webRTCModule.getLocalTrack(trackId)) != null
                || (track = remoteTracks.get(trackId)) != null) {
            peerConnection.getStats(
                reports -> cb.invoke(true, statsToJSON(reports)),
                    track);
        } else {
            Log.e(TAG, "peerConnectionGetStats() MediaStreamTrack not found for id: " + trackId);
            cb.invoke(false, "Track not found");
        }
    }

    /**
     * Constructs a JSON <tt>String</tt> representation of a specific array of
     * <tt>StatsReport</tt>s (produced by {@link PeerConnection#getStats}).
     * <p>
     * On Android it is faster to (1) construct a single JSON <tt>String</tt>
     * representation of an array of <tt>StatsReport</tt>s and (2) have it pass
     * through the React Native bridge rather than the array of
     * <tt>StatsReport</tt>s.
     *
     * @param reports the array of <tt>StatsReport</tt>s to represent in JSON
     * format
     * @return a <tt>String</tt> which represents the specified <tt>reports</tt>
     * in JSON format
     */
    private String statsToJSON(StatsReport[] reports) {
        // If possible, reuse a single StringBuilder instance across multiple
        // getStats method calls in order to reduce the total number of
        // allocations.
        StringBuilder s = statsToJSONStringBuilder.get();
        if (s == null) {
            s = new StringBuilder();
            statsToJSONStringBuilder = new SoftReference(s);
        }

        s.append('[');
        final int reportCount = reports.length;
        for (int i = 0; i < reportCount; ++i) {
            StatsReport report = reports[i];
            if (i != 0) {
                s.append(',');
            }
            s.append("{\"id\":\"").append(report.id)
                .append("\",\"type\":\"").append(report.type)
                .append("\",\"timestamp\":").append(report.timestamp)
                .append(",\"values\":[");
            StatsReport.Value[] values = report.values;
            final int valueCount = values.length;
            for (int j = 0; j < valueCount; ++j) {
                StatsReport.Value v = values[j];
                if (j != 0) {
                    s.append(',');
                }
                s.append("{\"").append(v.name).append("\":\"").append(v.value)
                    .append("\"}");
            }
            s.append("]}");
        }
        s.append("]");

        String r = s.toString();
        // Prepare the StringBuilder instance for reuse (in order to reduce the
        // total number of allocations performed during multiple getStats method
        // calls).
        s.setLength(0);

        return r;
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        Log.d(TAG, "onIceCandidate");
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        WritableMap candidateParams = Arguments.createMap();
        candidateParams.putInt("sdpMLineIndex", candidate.sdpMLineIndex);
        candidateParams.putString("sdpMid", candidate.sdpMid);
        candidateParams.putString("candidate", candidate.sdp);
        params.putMap("candidate", candidateParams);

        webRTCModule.sendEvent("peerConnectionGotICECandidate", params);
    }

    @Override
    public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
        Log.d(TAG, "onIceCandidatesRemoved");
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putString("iceConnectionState", iceConnectionStateString(iceConnectionState));

        webRTCModule.sendEvent("peerConnectionIceConnectionChanged", params);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean var1) {
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        Log.d(TAG, "onIceGatheringChange" + iceGatheringState.name());
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putString("iceGatheringState", iceGatheringStateString(iceGatheringState));
        webRTCModule.sendEvent("peerConnectionIceGatheringChanged", params);
    }

    private String getReactTagForStream(MediaStream mediaStream) {
        for (Iterator<Map.Entry<String, MediaStream>> i
                    = remoteStreams.entrySet().iterator();
                i.hasNext();) {
            Map.Entry<String, MediaStream> e = i.next();
            if (e.getValue().equals(mediaStream)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Listen onAddTrack
     * @deprecated
     */
    @Override
    public void onAddStream(MediaStream mediaStream) {
        Log.d(TAG, "onAddStream");
        String streamReactTag = null;
        String streamId = mediaStream.getId();
        // The native WebRTC implementation has a special concept of a default
        // MediaStream instance with the label default that the implementation
        // reuses.
        if ("default".equals(streamId)) {
            for (Map.Entry<String, MediaStream> e : remoteStreams.entrySet()) {
                if (e.getValue().equals(mediaStream)) {
                    streamReactTag = e.getKey();
                    break;
                }
            }
        }

        if (streamReactTag == null) {
            streamReactTag = UUID.randomUUID().toString();
            remoteStreams.put(streamReactTag, mediaStream);
        }

        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putString("streamId", streamId);
        params.putString("streamReactTag", streamReactTag);

        WritableArray tracks = Arguments.createArray();

        for (int i = 0; i < mediaStream.videoTracks.size(); i++) {
            VideoTrack track = mediaStream.videoTracks.get(i);
            String trackId = track.id();

            remoteTracks.put(trackId, track);

            WritableMap trackInfo = Arguments.createMap();
            trackInfo.putString("id", trackId);
            trackInfo.putString("label", "Video");
            trackInfo.putString("kind", track.kind());
            trackInfo.putBoolean("enabled", track.enabled());
            trackInfo.putString("readyState", track.state().toString());
            trackInfo.putBoolean("remote", true);
            tracks.pushMap(trackInfo);

            videoTrackAdapters.addAdapter(streamReactTag, track);
        }
        for (int i = 0; i < mediaStream.audioTracks.size(); i++) {
            AudioTrack track = mediaStream.audioTracks.get(i);
            String trackId = track.id();

            remoteTracks.put(trackId, track);

            WritableMap trackInfo = Arguments.createMap();
            trackInfo.putString("id", trackId);
            trackInfo.putString("label", "Audio");
            trackInfo.putString("kind", track.kind());
            trackInfo.putBoolean("enabled", track.enabled());
            trackInfo.putString("readyState", track.state().toString());
            trackInfo.putBoolean("remote", true);
            tracks.pushMap(trackInfo);
        }
        params.putArray("tracks", tracks);

        webRTCModule.sendEvent("peerConnectionAddedStream", params);
    }

    /**
     * PeerConnection.removeTrack(sender) used instead. There is no corresponding listener e.g onremovetrack for native unlike browser
     * @deprecated
     */
    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        String streamReactTag = getReactTagForStream(mediaStream);
        if (streamReactTag == null) {
            Log.w(TAG,
                "onRemoveStream - no remote stream for id: "
                    + mediaStream.getId());
            return;
        }

        for (VideoTrack track : mediaStream.videoTracks) {
            this.videoTrackAdapters.removeAdapter(track);
            this.remoteTracks.remove(track.id());
        }
        for (AudioTrack track : mediaStream.audioTracks) {
            this.remoteTracks.remove(track.id());
        }

        this.remoteStreams.remove(streamReactTag);

        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putString("streamId", streamReactTag);
        webRTCModule.sendEvent("peerConnectionRemovedStream", params);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        final int dataChannelId = dataChannel.id();
        if (-1 == dataChannelId) {
          return;
        }

        WritableMap dataChannelParams = Arguments.createMap();
        dataChannelParams.putInt("id", dataChannelId);
        dataChannelParams.putString("label", dataChannel.label());
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putMap("dataChannel", dataChannelParams);

        dataChannels.put(dataChannelId, dataChannel);
        registerDataChannelObserver(dataChannelId, dataChannel);

        webRTCModule.sendEvent("peerConnectionDidOpenDataChannel", params);
    }

    private void registerDataChannelObserver(int dcId, DataChannel dataChannel) {
        // DataChannel.registerObserver implementation does not allow to
        // unregister, so the observer is registered here and is never
        // unregistered
        dataChannel.registerObserver(
            new DataChannelObserver(webRTCModule, id, dcId, dataChannel));
    }

    @Override
    public void onRenegotiationNeeded() {
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        webRTCModule.sendEvent("peerConnectionOnRenegotiationNeeded", params);
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        WritableMap params = Arguments.createMap();
        params.putInt("id", id);
        params.putString("signalingState", signalingStateString(signalingState));
        webRTCModule.sendEvent("peerConnectionSignalingStateChanged", params);
    }

    @Override
    public void onAddTrack(final RtpReceiver receiver, final MediaStream[] mediaStreams) {
        Log.d(TAG, "START onAddTrack ------------------- " + "peerConnection ID: " + id);

        WritableMap params = Arguments.createMap();
        params.putInt("id", id);

        WritableArray streams = Arguments.createArray();

        for (int s = 0; s < mediaStreams.length; s++) {
            MediaStream stream = mediaStreams[s];
            String streamReactTag = null;
            String streamId = stream.getId();
            // The native WebRTC implementation has a special concept of a default
            // MediaStream instance with the label default that the implementation
            // reuses.
            if ("default".equals(streamId)) {
                for (Map.Entry<String, MediaStream> e : remoteStreams.entrySet()) {
                    if (e.getValue().equals(stream)) {
                        streamReactTag = e.getKey();
                        break;
                    }
                }
            }

            if (streamReactTag == null) {
                streamReactTag = UUID.randomUUID().toString();
                remoteStreams.put(streamReactTag, stream);
            }

            WritableMap streamInfo = Arguments.createMap();
            streamInfo.putString("id", streamId);
            streamInfo.putString("streamId", streamId);
            streamInfo.putString("streamReactTag", streamReactTag);

            WritableArray tracks = Arguments.createArray();

            for (int i = 0; i < stream.videoTracks.size(); i++) {
                VideoTrack track = stream.videoTracks.get(i);
                String trackId = track.id();

                remoteTracks.put(trackId, track);

                WritableMap trackInfo = Arguments.createMap();
                trackInfo.putString("id", trackId);
                trackInfo.putString("label", "Video");
                trackInfo.putString("kind", track.kind());
                trackInfo.putBoolean("enabled", track.enabled());
                trackInfo.putString("readyState", track.state().toString());
                trackInfo.putBoolean("remote", true);
                tracks.pushMap(trackInfo);

                videoTrackAdapters.addAdapter(streamReactTag, track);
                Log.d(TAG, "video track added - id: " + trackId);
            }
            for (int i = 0; i < stream.audioTracks.size(); i++) {
                AudioTrack track = stream.audioTracks.get(i);
                String trackId = track.id();

                remoteTracks.put(trackId, track);

                WritableMap trackInfo = Arguments.createMap();
                trackInfo.putString("id", trackId);
                trackInfo.putString("label", "Audio");
                trackInfo.putString("kind", track.kind());
                trackInfo.putBoolean("enabled", track.enabled());
                trackInfo.putString("readyState", track.state().toString());
                trackInfo.putBoolean("remote", true);
                tracks.pushMap(trackInfo);

                Log.d(TAG, "audio track added - id: " + trackId);
            }
            streamInfo.putArray("tracks", tracks);
            streams.pushMap(streamInfo);
        }

        params.putArray("streams", streams);

        // Retrieve transceiverMid from getTransceiver() and match it with returned receiver
        List<RtpTransceiver> pcTransceivers = peerConnection.getTransceivers();
        for (int t = 0; t < pcTransceivers.size(); t++) {
            RtpTransceiver transceiver = pcTransceivers.get(t);
            RtpReceiver t_receiver = transceiver.getReceiver();
            String t_receiverId = new String(t_receiver.id());
            String receiverId = new String(receiver.id());
            if (t_receiverId.equals(receiverId)) {
                String transceiverMid = transceiver.getMid();
                params.putString("transceiverMid", transceiverMid);
                Log.d(TAG, "transceiverMid: " + transceiverMid);
            }
        }

        // This is the only way to pass transceivers list to peerConnection.js to be stored locally
        WritableArray transceivers = getTransceivers();
        params.putArray("transceivers", transceivers);
        webRTCModule.sendEvent("peerConnectionAddTrack", params);
        Log.d(TAG, "END onAddTrack -------------------");
    }

    /**
     * Native getTransceivers
     * @return
     */
   public WritableArray getTransceivers() {
        WritableArray m_transceivers = Arguments.createArray();
        if (peerConnection != null) {
            List<RtpTransceiver> transceivers = peerConnection.getTransceivers();
            // for each transceiver in list build writeableMap
            Log.d(TAG, "START pc.getTransceivers ------------------- " + "peerConnection ID: " + id);
                for (int t = 0; t < transceivers.size(); t++) {
                    WritableMap transceiverInfo = Arguments.createMap();
                    RtpTransceiver transceiver = transceivers.get(t);
                    MediaStreamTrack.MediaType mediaType = transceiver.getMediaType();
                    String transceiverMid = transceiver.getMid();
                    RtpSender sender = transceiver.getSender();
                    RtpReceiver receiver = transceiver.getReceiver();
                    RtpTransceiver.RtpTransceiverDirection transceiverDirection = transceiver.getDirection();
                    transceiverInfo.putString("transceiverDirection", transceiverDirection.toString());
                    transceiverInfo.putString("transceiverMid", transceiverMid);
                    Log.d(TAG, "transceiverDirection: " + transceiverDirection);
                    Log.d(TAG, "transceiverMid: " + transceiverMid);
                    // build senderInfo
                    MediaStreamTrack senderTrack = sender.track();
                    if (senderTrack != null) {
                        String senderTrackId = senderTrack.id();
                        transceiverInfo.putString("senderTrackId", senderTrackId);
                        Log.d(TAG, "senderTrackId: " + senderTrackId);
                    }
                    // build receiverInfo
                    MediaStreamTrack receiverTrack = receiver.track();
                    if (receiverTrack != null) {
                        String receiverTrackId = receiverTrack.id();
                        transceiverInfo.putString("receiverTrackId", receiverTrackId);
                        Log.d(TAG, "receiverTrackId: " + receiverTrackId);
                    }
                    m_transceivers.pushMap(transceiverInfo);
                }
            Log.d(TAG, "END pc.getTransceivers -------------------");
        }
        return m_transceivers;
    }

    @Nullable
    private String iceConnectionStateString(PeerConnection.IceConnectionState iceConnectionState) {
        switch (iceConnectionState) {
            case NEW:
                return "new";
            case CHECKING:
                return "checking";
            case CONNECTED:
                return "connected";
            case COMPLETED:
                return "completed";
            case FAILED:
                return "failed";
            case DISCONNECTED:
                return "disconnected";
            case CLOSED:
                return "closed";
        }
        return null;
    }

    @Nullable
    private String iceGatheringStateString(PeerConnection.IceGatheringState iceGatheringState) {
        switch (iceGatheringState) {
            case NEW:
                return "new";
            case GATHERING:
                return "gathering";
            case COMPLETE:
                return "complete";
        }
        return null;
    }

    @Nullable
    private String signalingStateString(PeerConnection.SignalingState signalingState) {
        switch (signalingState) {
            case STABLE:
                return "stable";
            case HAVE_LOCAL_OFFER:
                return "have-local-offer";
            case HAVE_LOCAL_PRANSWER:
                return "have-local-pranswer";
            case HAVE_REMOTE_OFFER:
                return "have-remote-offer";
            case HAVE_REMOTE_PRANSWER:
                return "have-remote-pranswer";
            case CLOSED:
                return "closed";
        }
        return null;
    }
}
