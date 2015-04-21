package com.ryong21.io.net;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IoConstants;
import org.red5.io.flv.Tag;
import org.red5.io.utils.ObjectMap;
import org.red5.server.event.IEvent;
import org.red5.server.event.IEventDispatcher;
import org.red5.server.messaging.IMessage;
import org.red5.server.net.rtmp.INetStreamEventHandler;
import org.red5.server.net.rtmp.RTMPClient;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.FlexStreamSend;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Invoke;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.Ping;
import org.red5.server.net.rtmp.event.Unknown;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.service.IPendingServiceCall;
import org.red5.server.service.IPendingServiceCallback;
import org.red5.server.stream.IStreamData;
import org.red5.server.stream.message.RTMPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ryong21.decode.Decoder;
import com.ryong21.decode.SpeexDecoder;
import com.ryong21.io.Consumer;
import com.ryong21.manager.PlayStatusListener;

/**
 * A publish client to publish stream to server.
 */
public class NetClient implements IEventDispatcher, INetStreamEventHandler,
		IPendingServiceCallback {

	private static Logger log = LoggerFactory.getLogger(NetClient.class);
	private List<IMessage> frameBuffer = new ArrayList<IMessage>();

	public static final int STOPPED = 0;
	public static final int CONNECTING = 1;
	public static final int STREAM_CREATING = 2;
	public static final int PUBLISHING = 3;
	private static final int PLAYING = 3;
	public static final int PUBLISHED = 4;

	public static final int PUBLISH = 1;
	public static final int PLAY = 2;

	private String host;
	private int port;
	private String app;
	private int state;
	private int duration = 10 * 1000;
	private int start = 0;
	private int playLen = 10;
	private String publishName;
	private String playFileName;
	private int streamId;
	private String publishMode;
	private RTMPClient rtmpClient;
	private int prevSize = 0;
	private Tag tag;
	private int currentTime = 0;
	private long timeBase = 0;
	private int sampleRate = 0;
	private int channel;
	private int mode;
	private Decoder decoder = null;
	private Consumer consumer;
	private PlayStatusListener listener;
	
	public synchronized void publish(String publishName, String publishMode,
			Object[] params) {
		state = CONNECTING;
		this.publishName = publishName;
		this.publishMode = publishMode;
		this.mode = PUBLISH;
		startClientConnect(params);
	}

	public synchronized void play(String playName, int start, int duration,
			Object[] params) {
		state = CONNECTING;
		this.playFileName = playName;
		this.start = start;
		this.duration = duration;
		this.mode = PLAY;
		startClientConnect(params);
	}

	private void startClientConnect(Object[] params) {
		rtmpClient = new RTMPClient();
		rtmpClient.setStreamEventDispatcher(this);
		Map<String, Object> defParams = rtmpClient.makeDefaultConnectionParams(
				host, port, app);
		rtmpClient.connect(host, port, defParams, this, params);
	}

	public synchronized void stop() {
		if (state >= STREAM_CREATING) {
			rtmpClient.disconnect();
		}
		state = STOPPED;
		if(listener != null){
			listener.OnPlayStatus(STOPPED);
		}
	}

	public synchronized void onStreamEvent(Notify notify) {
		log.debug("onStreamEvent: {}", notify);
		ObjectMap<?, ?> map = (ObjectMap<?, ?>) notify.getCall().getArguments()[0];
		String code = (String) map.get("code");
		log.debug("<:{}", code);
		if (StatusCodes.NS_PUBLISH_START.equals(code)) {
			state = PUBLISHED;
			while (frameBuffer.size() > 0) {
				rtmpClient.publishStreamData(streamId, frameBuffer.remove(0));
			}
		}
	}

	public synchronized void resultReceived(IPendingServiceCall call) {
		log.debug("resultReceived:> {}", call.getServiceMethodName());
		if ("connect".equals(call.getServiceMethodName())) {
			state = STREAM_CREATING;
			rtmpClient.createStream(this);
		} else if ("createStream".equals(call.getServiceMethodName())) {
			Object result = call.getResult();
			if (result instanceof Integer) {
				Integer streamIdInt = (Integer) result;
				streamId = streamIdInt.intValue();

				if (this.mode == PUBLISH) {
					rtmpClient.publish(streamIdInt.intValue(), publishName,
							publishMode, this);
					state = PUBLISHING;
				} else if (this.mode == PLAY) {
					log.debug("Playing state{} name {} ", state, playFileName);
					rtmpClient.play(streamIdInt, playFileName, start, duration);

					Ping ping = new Ping();
					ping.setEventType(Ping.CLIENT_BUFFER);
					ping.setValue2(streamId);
					ping.setValue3(2000);
					rtmpClient.getConnection().ping(ping);
					rtmpClient.setServiceProvider(this);
					state = PLAYING;
				}

			} else {
				rtmpClient.disconnect();
				state = STOPPED;
			}
		}
	}

	public void dispatchEvent(IEvent event) {
		IRTMPEvent rtmpEvent = (IRTMPEvent) event;

		if (!(rtmpEvent instanceof IStreamData)) {
			log.debug("skipping non stream data");
			return;
		}
		if (rtmpEvent.getHeader().getSize() == 0) {
			log.debug("skipping event where size == 0");
			return;
		}

		if (rtmpEvent instanceof VideoData) {
			//play video here
		} else if (rtmpEvent instanceof AudioData) {
			IoBuffer buf = ((AudioData) rtmpEvent).getData();
			byte tagType = buf.get();
			if (decoder == null) {
				byte codec = (byte) (tagType & 0xF0);
				if (codec == (byte) (IoConstants.FLAG_FORMAT_SPEEX << 4)) {
					decoder = new SpeexDecoder(this.consumer);
					Thread decodeThread = new Thread((Runnable) decoder);
					decoder.setRunning(true);
					decodeThread.start();
					log.debug("start SpeexDecoder");
				} else if (codec == (byte) (IoConstants.FLAG_FORMAT_NELLYMOSER << 4)) {

					// start nellymoser decoder here

				} else {
					return;
				}
			}
			int size = buf.remaining();
			byte[] data = new byte[size];
			buf.get(data, 0, size);
			if (decoder.isIdle()) {
				decoder.putData(rtmpEvent.getTimestamp(), data, size);
			} else {
				log.error("drop data size {} ts{}", size,
						rtmpEvent.getTimestamp());
			}
		}
		if (rtmpEvent.getTimestamp() / 1000 > playLen) {
			log.debug("play progress: {} seconds", playLen);
			playLen += 10;
		}
	}

	@SuppressWarnings("rawtypes")
	public void onStatus(Object obj) {
		ObjectMap map = (ObjectMap) obj;
		String code = (String) map.get("code");
		String description = (String) map.get("description");
		String details = (String) map.get("details");

		if (StatusCodes.NS_PLAY_RESET.equals(code)) {
			log.debug("{}: {}", new Object[] { code, description });
		} else if (StatusCodes.NS_PLAY_START.equals(code)) {
			log.info("playing name: " + playFileName);
			log.error("{}: {}", new Object[] { code, description });
		} else if (StatusCodes.NS_PLAY_STOP.equals(code)) {
			state = STOPPED;
			log.error("{}: {}", new Object[] { code, description });
			log.info("Recording Complete");
			rtmpClient.disconnect();
			stop();
		} else if (StatusCodes.NS_PLAY_STREAMNOTFOUND.equals(code)) {
			state = STOPPED;
			log.info("File {} Not found", new Object[] { details });
			log.debug("{} for {}", new Object[] { code, details });
			rtmpClient.disconnect();
			stop();
		}
	}

	public void writeTag(byte[] buf, int size, long ts) {
		if (timeBase == 0) {
			timeBase = ts;
		}
		currentTime = (int) (ts - timeBase);
		tag = new Tag(IoConstants.TYPE_AUDIO, currentTime, size + 1, null,
				prevSize);
		prevSize = size + 1;

		byte tagType = (byte) ((IoConstants.FLAG_FORMAT_SPEEX << 4))
				| (IoConstants.FLAG_SIZE_16_BIT << 1);
		switch (sampleRate) {
		case 44100:
			tagType |= IoConstants.FLAG_RATE_44_KHZ << 2;
			break;
		case 22050:
			tagType |= IoConstants.FLAG_RATE_22_KHZ << 2;
			break;
		case 11025:
			tagType |= IoConstants.FLAG_RATE_11_KHZ << 2;
			break;
		default:
			tagType |= IoConstants.FLAG_RATE_5_5_KHZ << 2;
		}

		tagType |= (channel == 2 ? IoConstants.FLAG_TYPE_STEREO
				: IoConstants.FLAG_TYPE_MONO);

		IoBuffer body = IoBuffer.allocate(tag.getBodySize());
		body.setAutoExpand(true);
		body.put(tagType);
		body.put(buf);
		body.flip();
		body.limit(tag.getBodySize());
		tag.setBody(body);

		IMessage msg = makeMessageFromTag(tag);
		try {
			pushMessage(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public IMessage makeMessageFromTag(Tag tag) {
		IRTMPEvent msg = null;
		switch (tag.getDataType()) {
		case Constants.TYPE_AUDIO_DATA:
			msg = new AudioData(tag.getBody());
			break;
		case Constants.TYPE_VIDEO_DATA:
			msg = new VideoData(tag.getBody());
			break;
		case Constants.TYPE_INVOKE:
			msg = new Invoke(tag.getBody());
			break;
		case Constants.TYPE_NOTIFY:
			msg = new Notify(tag.getBody());
			break;
		case Constants.TYPE_FLEX_STREAM_SEND:
			msg = new FlexStreamSend(tag.getBody());
			break;
		default:
			log.warn("Unexpected type? {}", tag.getDataType());
			msg = new Unknown(tag.getDataType(), tag.getBody());
		}
		msg.setTimestamp(tag.getTimestamp());
		RTMPMessage rtmpMsg = new RTMPMessage();
		rtmpMsg.setBody(msg);
		rtmpMsg.getBody();
		return rtmpMsg;
	}

	synchronized public void pushMessage(IMessage message) throws IOException {
		if (state >= PUBLISHED && message instanceof RTMPMessage) {
			RTMPMessage rtmpMsg = (RTMPMessage) message;
			rtmpClient.publishStreamData(streamId, rtmpMsg);
		} else {
			frameBuffer.add(message);
		}
	}

	public int getState() {
		return state;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setApp(String app) {
		this.app = app;
	}

	public void setSampleRate(int sampleRate) {
		this.sampleRate = sampleRate;
	}

	public void setChannel(int channel) {
		this.channel = channel;
	}

	public void setConsumer(Consumer consumer) {
		this.consumer = consumer;
	}
	
	public void setListener(PlayStatusListener listener) {
		this.listener = listener;
	}

}
