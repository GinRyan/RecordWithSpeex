package com.ryong21.encode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ryong21.io.Consumer;

public class Encoder implements Runnable {

	private Logger log = LoggerFactory.getLogger(Encoder.class);
	private volatile int leftSize = 0;
	private final Object mutex = new Object();
	private Speex speex = new Speex();
	private int frameSize;
	private Consumer consumer;
	private byte[] processedData = new byte[1024];
	private short[] rawdata = new short[1024];
	private volatile boolean isRunning;

	public Encoder(Consumer consumer) {
		super();
		this.consumer = consumer;
		speex.init();
		frameSize = speex.getFrameSize();
		log.debug("frameSize {}", frameSize);
	}

	public void run() {

		android.os.Process
				.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

		int getSize = 0;
		while (this.isRunning()) {
			synchronized (mutex) {
				while (isIdle()) {
					try {
						mutex.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			synchronized (mutex) {
				short[] temp = new short[frameSize];
				for (int i = 0; i < 4; i++) {
					System.arraycopy(rawdata, i * frameSize, temp, 0, frameSize);
					getSize = speex.encode(temp, 0, processedData, frameSize);
//					log.error("encoded size {}", getSize);
					if (getSize > 0) {
						consumer.putData(System.currentTimeMillis(), processedData, getSize);
					}
				}
			}
			setIdle();
		}
	}

	public void putData(short[] data, int size) {
		synchronized (mutex) {
			System.arraycopy(data, 0, rawdata, 0, size);
			this.leftSize = size;
			mutex.notify();
		}
	}

	public boolean isIdle() {
		return leftSize == 0 ? true : false;
	}

	public void setIdle() {
		leftSize = 0;
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
