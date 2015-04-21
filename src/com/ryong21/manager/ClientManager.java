package com.ryong21.manager;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import com.ryong21.io.Consumer;
import com.ryong21.io.PcmRecorder;
import com.ryong21.io.file.FileClient;
import com.ryong21.io.net.NetClient;

public class ClientManager implements Runnable, Consumer, PlayStatusListener {
	private Logger log = LoggerFactory.getLogger(ClientManager.class);
	private final Object mutex = new Object();
	// NONE = live
	public static final int NONE = 0;
	public static final int NETONLY = 1;
	public static final int FILEONLY = 2;
	public static final int NETANDFILE = 3;
	private int recordMode = NETONLY;
	//record or playback
	public static final int SING = 1;
	public static final int PLAY = 2;
	private int clientMode = SING;

	private int seq = 1;
	private int duration = 10 * 1000;
	private int start = 0;
	private volatile boolean isRunning;
	private volatile boolean isNeedExit;
	private EncodedData eData;
	private DecodedData dData;
	private List<EncodedData> encodeList;
	private List<DecodedData> decodeList;
	private String publishNameBase = "test";
	private String publishName;
	private String playName;
	private String fileNameBase = "/mnt/sdcard/test";
	private String fileName;
	private PcmRecorder recorder = null;
	private NetClient netClient = new NetClient();
	private FileClient fileClient = new FileClient();
	private AudioTrack audioTrack;

	public ClientManager() {
		super();
		encodeList = Collections
				.synchronizedList(new LinkedList<EncodedData>());
		decodeList = Collections
				.synchronizedList(new LinkedList<DecodedData>());
	}

	public void run() {
		log.debug("publish thread runing");

		netClientInit();
		fileClientInit();

		while (!this.isNeedExit()) {
			synchronized (mutex) {
				while (!this.isRunning) {
					try {
						mutex.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

			setupParams();
			if (this.clientMode == SING) {
				startSingClient();
				startPcmRecorder();
				while (this.isRunning()) {
					if (encodeList.size() > 0) {
						writeTag();
					} else {
						try {
							Thread.sleep(20);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			} else {
				startPlayClient();
				startAudioTrack();
				while (this.isRunning()) {
					if (decodeList.size() > 50) {
						playTag();
					} else {
						try {
							Thread.sleep(20);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
			stop();
		}
	}

	private void setupParams() {
		if (this.clientMode == SING) {
			fileName = fileNameBase + seq + ".flv";
			publishName = publishNameBase + seq;
			playName = publishName + ".flv";
			start = 0;
			duration = -2;
			seq++;
		}
	}

	private void writeTag() {
		eData = encodeList.remove(0);
		if (this.recordMode == NETONLY || this.recordMode == NETANDFILE) {
			netClient.writeTag(eData.processed, eData.size, eData.ts);
		}
		if (this.recordMode == FILEONLY || this.recordMode == NETANDFILE) {
			fileClient.writeTag(eData.processed, eData.size, eData.ts);
		}
	}

	private void playTag() {
		while (decodeList.size() > 0 && this.isRunning) {
			dData = decodeList.remove(0);
			log.error("play {}",dData.ts);
			audioTrack.write(dData.processed, 0, dData.size);
		}
	}
	
	private void startPlayClient() {
		netClient.play(playName, start, duration, null);
	}

	private void startSingClient() {
		switch (this.recordMode) {
		case NONE:
			netClient.publish(publishName, "live", null);
			break;
		case NETONLY:
			netClient.publish(publishName, "record", null);
			break;
		case FILEONLY:
			fileClient.start(fileName);
			break;
		case NETANDFILE:
			netClient.publish(publishName, "record", null);
			fileClient.start(fileName);
			break;
		default:
			netClient.publish(publishName, "record", null);
		}
	}

	private void startPcmRecorder() {
		recorder = new PcmRecorder(this);
		recorder.setRunning(true);
		Thread th = new Thread(recorder);
		th.start();
	}

	private void startAudioTrack() {
		int bufferSizeInBytes = AudioTrack.getMinBufferSize(8000,
				AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 8000,
				AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
				2 * bufferSizeInBytes, AudioTrack.MODE_STREAM);
		audioTrack.play();
	}
	
	private void netClientInit() {
		netClient.setHost("192.168.1.102");
		netClient.setPort(1935);
		netClient.setApp("live");
		netClient.setChannel(1);
		netClient.setSampleRate(8000);
		netClient.setConsumer(this);
	}

	private void fileClientInit() {
		fileClient.setChannel(1);
		fileClient.setSampleRate(8000);
	}

	public void setRecordMode(int mode) {
		this.recordMode = mode;
	}

	public void setClientMode(int mode) {
		this.clientMode = mode;
	}

	private void stop() {
		netClient.stop();
		if (this.clientMode == SING) {
			recorder.stop();
			fileClient.stop();
		} else {
			audioTrack.stop();
			audioTrack.release();
		}
	}

	public boolean isNeedExit() {
		synchronized (mutex) {
			return isNeedExit;
		}
	}

	public void setNeedExit(boolean isNeedExit) {
		synchronized (mutex) {
			this.isNeedExit = isNeedExit;
			if (this.isNeedExit) {
				mutex.notify();
			}
		}
	}

	public void setRunning(boolean isRunning) {
		synchronized (mutex) {
			this.isRunning = isRunning;
			if (this.isRunning) {
				mutex.notify();
			}
		}
	}

	public boolean isRunning() {
		synchronized (mutex) {
			return isRunning;
		}
	}
	
	public void putData(long ts, short[] buf, int size) {
		DecodedData data = new DecodedData();
		data.ts = ts;
		data.size = size;
		System.arraycopy(buf, 0, data.processed, 0, size);
		decodeList.add(data);
	}

	public void putData(long ts, byte[] buf, int size) {
		EncodedData data = new EncodedData();
		data.ts = ts;
		data.size = size;
		System.arraycopy(buf, 0, data.processed, 0, size);
		encodeList.add(data);
	}
	
	class EncodedData {
		private long ts;
		private int size;
		private byte[] processed = new byte[256];
	}

	class DecodedData {
		private long ts;
		private int size;
		private short[] processed = new short[256];
	}

	@Override
	public void OnPlayStatus(int status) {		
		if(status == NetClient.STOPPED){
			setRunning(false);
			log.error("play stoped");
		}		
	}
}
