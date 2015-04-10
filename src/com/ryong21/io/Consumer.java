package com.ryong21.io;

public interface Consumer {
	
	public void putData(long ts, byte[] buf, int size);
	
	public void setRunning(boolean isRunning);
	
	public boolean isRunning();

	public void putData(long ts, short[] processedData, int getSize);	
}
