package com.ryong21.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ryong21.encode.Encoder;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class PcmRecorder implements Runnable {

	private Logger log = LoggerFactory.getLogger(PcmRecorder.class);
	private volatile boolean isRunning;
	private final Object mutex = new Object();
	private static final int frequency = 8000;
	private static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
	private Consumer consumer;
	public PcmRecorder(Consumer consumer) {
		super();
		this.consumer = consumer;
	}

	public void run() {

		Encoder encoder = new Encoder(this.consumer);
		Thread encodeThread = new Thread(encoder);
		encoder.setRunning(true);
		encodeThread.start();

		android.os.Process
				.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

		int bufferRead = 0;
		int bufferSize = AudioRecord.getMinBufferSize(frequency,
				AudioFormat.CHANNEL_IN_MONO, audioEncoding);
		
		short[] tempBuffer = new short[bufferSize];
		AudioRecord recordInstance = new AudioRecord(
				MediaRecorder.AudioSource.MIC, frequency,
				AudioFormat.CHANNEL_IN_MONO, audioEncoding, bufferSize);

		recordInstance.startRecording();

		while (this.isRunning) {

			//bufferRead = recordInstance.read(tempBuffer, 0, bufferSize);
			bufferRead = recordInstance.read(tempBuffer, 0, bufferSize);
			if (bufferRead == AudioRecord.ERROR_INVALID_OPERATION) {
				throw new IllegalStateException(
						"read() returned AudioRecord.ERROR_INVALID_OPERATION");
			} else if (bufferRead == AudioRecord.ERROR_BAD_VALUE) {
				throw new IllegalStateException(
						"read() returned AudioRecord.ERROR_BAD_VALUE");
			} else if (bufferRead == AudioRecord.ERROR_INVALID_OPERATION) {
				throw new IllegalStateException(
						"read() returned AudioRecord.ERROR_INVALID_OPERATION");
			}

			if (encoder.isIdle()) {
				encoder.putData(tempBuffer, bufferRead);
			} else {
				// 认为编码处理不过来，直接丢掉这次读到的数据
				log.error("drop data!");
			}

		}
		recordInstance.stop();
		encoder.setRunning(false);
	}

	public void stop(){
		setRunning(false);
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
}
