package com.ryong21.decode;


import com.ryong21.io.Consumer;
import com.ryong21.encode.Speex;

public class SpeexDecoder implements Runnable, Decoder {
	private volatile int leftSize = 0;
	private final Object mutex = new Object();
	private Speex speex = new Speex();
	private long ts;
	private Consumer consumer;
	private short[] processedData = new short[256];
	private byte[] rawdata = new byte[256];
	private volatile boolean isRunning;

	public SpeexDecoder(Consumer consumer) {
		super();
		this.consumer = consumer;
		speex.init();
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
				getSize = speex.decode(rawdata, processedData, leftSize);
				consumer.putData(ts, processedData, getSize);								
				setIdle();
			}
		}
	}

	public void putData(long ts, byte[] data, int size) {
		synchronized (mutex) {
			this.ts = ts;
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
