package com.example.dashclient;

import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import com.sonymobile.common.AccessUnit;
import com.sonymobile.common.SendObject;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class AudioDecodeThread extends Thread {
	private static final int TIMEOUT_US = 1000;
	private static final String AUDIO_CODEC_NAME = "OMX.google.aac.decoder";
	private static final String AUDIO_MIME = "audio/mp4a-latm";
	private static final String TAG = "AudioDecodeThread";
	private MediaCodec codec;
	
	private boolean isEos;
	private int mSampleRate = 44100;
	private int mChannelCount = 2;

	/*
	 * To receive from seeder
	 */
	private static final int MSG_CONNECT = 1;
	private static final int MSG_READ = 2;
	private static final int MSG_CLOSE = 3;
	private Socket mSocket;
	private HandlerThread mSocketThread;
	private SocketHandler mSocketHandler;
	private ObjectInputStream mSocketInputStream;
	private BlockingQueue<SendObject> mQueue = new LinkedBlockingDeque<SendObject>(30);
	private Object mConnectionLock = new Object();

	public AudioDecodeThread() {
		
		mSocketThread = new HandlerThread("Socket");
		mSocketThread.start();
		mSocketHandler = new SocketHandler(mSocketThread.getLooper());

		mSocketHandler.sendEmptyMessage(MSG_CONNECT);	
		
		isEos = false;
		codec = MediaCodec.createByCodecName(AUDIO_CODEC_NAME);
		MediaFormat audioFormat = makeAACData(MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		codec.configure(audioFormat, null, null, 0);
		codec.start();
	}
	
	private MediaFormat makeAACData(int audioProfile){
		MediaFormat format = MediaFormat.createAudioFormat(AUDIO_MIME, mSampleRate, mChannelCount);
		int sampleIndex = 4;
		
		ByteBuffer csd = ByteBuffer.allocate(2);
		csd.put((byte) ((audioProfile << 3) | (sampleIndex >> 1)));
		
		csd.position(1);
		csd.put((byte) ((byte) ((sampleIndex << 7) & 0x80) | (mChannelCount << 3)));
		csd.flip();
		format.setByteBuffer("csd-0", csd); // add csd-0
		
		return format;
	}
	
	@Override
	public void run(){
		Log.i(TAG, "run");
		ByteBuffer[] inputBuffers = codec.getInputBuffers();
		ByteBuffer[] outputBuffers = codec.getOutputBuffers();
		
		BufferInfo info = new BufferInfo();
		
		int buffersize = AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
		AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffersize,
                AudioTrack.MODE_STREAM);
		audioTrack.play();
		Log.i(TAG, "audio track play");
		while (!isEos) {
			try {
				int inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US);
				if (inputBufferIndex >= 0) {
					SendObject sendObject = mQueue.take();
					AccessUnit accessUnit = sendObject.makeAccessUnit();
					Log.i(TAG, "timeMs: " + accessUnit.timeUs / 1000);
					if (accessUnit.status == AccessUnit.OK) {
						inputBuffers[inputBufferIndex].position(0);
						inputBuffers[inputBufferIndex].put(accessUnit.data, 0,
								accessUnit.size);
						codec.queueInputBuffer(inputBufferIndex, 0,
								accessUnit.size, accessUnit.timeUs,
								MediaCodec.BUFFER_FLAG_SYNC_FRAME);

					}

				}
			} catch (Exception e) {
			}
			
			int outputBufferIndex = codec.dequeueOutputBuffer(info, 0);
			if(outputBufferIndex >= 0){
				ByteBuffer outBuffer = outputBuffers[outputBufferIndex];
				final byte[] chunk = new byte[info.size];
				outBuffer.get(chunk);
				outBuffer.clear();
				
				audioTrack.write(chunk, info.offset, info.offset+info.size);
				codec.releaseOutputBuffer(outputBufferIndex, false);
				
			}
		}
		
		codec.stop();
		codec.release();
		codec = null;
		
		audioTrack.stop();
		audioTrack.release();
		audioTrack = null;
	}
	
	public void close(){
		isEos = true;
	}
	
	 private void onConnect() {
			try {
				synchronized (mConnectionLock) {
					if (mSocket == null || !mSocket.isConnected()) {
						mSocket = new Socket();
						mSocket.connect(new InetSocketAddress("192.168.49.1", 55100));
						mSocketInputStream = new ObjectInputStream(mSocket.getInputStream());
						Log.i(TAG,"onConnect");
						mSocketHandler.sendEmptyMessage(MSG_READ);
						this.start();
					}
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
				try {
					Thread.sleep(1000);
				} catch (Exception e2) {}
				mSocketHandler.sendEmptyMessage(MSG_CONNECT);
			}
		}

		private void onRead() {
			try {
				Log.i(TAG, "reading...");
				mQueue.put((SendObject)mSocketInputStream.readObject());
				mSocketHandler.sendEmptyMessage(MSG_READ);
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
				mSocketHandler.sendEmptyMessage(MSG_CLOSE);
			}
		}

		public void onClose() {
			try {
				if (mSocket != null && mSocket.isConnected()) {
					mSocketInputStream.close();
					mSocketInputStream = null;
					mSocket.close();
					mSocket = null;
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	
	
	private class SocketHandler extends Handler {
		public SocketHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_CONNECT:
				Log.i(TAG, "before connect");
				onConnect();
				Log.i(TAG, "after connect");
				break;
			case MSG_READ:
				onRead();
				break;
			case MSG_CLOSE:
				onClose();
				break;
			default:
				Log.w(TAG, "unknown message");
				break;
			}
		}
	}
	
}
