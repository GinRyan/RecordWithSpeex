
package com.ryong21.encode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Speex  {

	/**
	 * quality
	 * 1 : 4kbps (very noticeable artifacts, usually intelligible)
	 * 2 : 6kbps (very noticeable artifacts, good intelligibility)
	 * 4 : 8kbps (noticeable artifacts sometimes)
	 * 6 : 11kpbs (artifacts usually only noticeable with headphones)
	 * 8 : 15kbps (artifacts not usually noticeable)
	 */
	private static final int DEFAULT_COMPRESSION = 8;
	private Logger log = LoggerFactory.getLogger(Speex.class);
	
	public Speex() {
	}
	/**
	 * ��ʼ���������
	 */
	public void init() {
		load();	
		open(DEFAULT_COMPRESSION);
		log.debug("speex opened");	
	}
	/**
	 * ����speex���ؿ�
	 */
	private void load() {
		try {
			System.loadLibrary("speex");
		} catch (Throwable e) {
			e.printStackTrace();
		}

	}
	/**
	 * ����һ����������Ķ���������̳�ʼ�����������ʡ�������֡��С�ȵ�
	 * @param compression ѹ������
	 * @return �����ɹ��򷵻�0�������ǰ�Ѿ������������Ƿ���0
	 */
	public native int open(int compression);
	/**
	 * ��ȡ����֡��С 
	 * @return �������ڱ����֡��С
	 */
	public native int getFrameSize();
	/**
	 * ��һ���Ѿ�speex������ֽ�������н���
	 * @param encoded �Ѿ���speex������ֽ�����
	 * @param lin ������ԭʼPCM����
	 * @param size ԭ����֡��С
	 * @return �����֡��С
	 */
	public native int decode(byte encoded[], short lin[], int size);
	/**
	 * ��һ��ԭʼPCM������ֽ��������Ϊspeex
	 * @param lin �������ԭʼPCM����
	 * @param offset ÿ��ȡ������Ч���ݿ�ʼ��ƫ������һ��Ϊ0
	 * @param encoded ���speex����������
	 * @param size ֡��С(��������)
	 * @return  ���ر������ֽ����鳤��
	 */
	public native int encode(short lin[], int offset, byte encoded[], int size);
	/**
	 * �رձ�������
	 */
	public native void close();
	
}
