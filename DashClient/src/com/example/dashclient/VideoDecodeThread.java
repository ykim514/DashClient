package com.example.dashclient;

import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import com.sonymobile.common.AccessUnit;
import com.sonymobile.common.SendObject;

public class VideoDecodeThread extends Thread{
	private static final String VIDEO_CODEC_NAME = "OMX.qcom.video.decoder.avc";
	private static final String VIDEO_MIME = "video/avc";
	private static final String TAG = "VideoDecodeThread";
	private MediaCodec codec;
	
	private boolean isEos;
	
	/*
	 * To receive from peer
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
	
	public VideoDecodeThread(Surface surface){
		
		mSocketThread = new HandlerThread("Socket");
		mSocketThread.start();
		mSocketHandler = new SocketHandler(mSocketThread.getLooper());

		mSocketHandler.sendEmptyMessage(MSG_CONNECT);	
		
		isEos = false;
		codec = MediaCodec.createByCodecName(VIDEO_CODEC_NAME);
		MediaFormat videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME, 1080, 720);
		codec.configure(videoFormat, surface, null, 0);
		codec.start();
	}
	
	@Override
	public void run(){
		Log.i(TAG, "run");
		
		BufferInfo info = new BufferInfo();
		ByteBuffer[] inputBuffers = codec.getInputBuffers();
		codec.getOutputBuffers();
		
		boolean first = false;
		long startWhen = 0;
		
		while (!isEos) {
			try {
				int inputBufferIndex = codec.dequeueInputBuffer(1000);
				if (inputBufferIndex >= 0) {
					Log.i(TAG, "mQueue: "+mQueue.size());

					SendObject sendObject = mQueue.take();
					AccessUnit accessUnit = sendObject.makeAccessUnit();
					Log.i(TAG, "timeMs: " + accessUnit.timeUs / 1000);
					if (accessUnit.status == AccessUnit.OK) {
						inputBuffers[inputBufferIndex].position(0);
						inputBuffers[inputBufferIndex].put(accessUnit.data, 0,
								accessUnit.size);
						codec.queueInputBuffer(
								inputBufferIndex,
								0,
								accessUnit.size,
								accessUnit.timeUs,
								accessUnit.isSyncSample ? MediaCodec.BUFFER_FLAG_SYNC_FRAME
										: 0);
					} else {
						Log.e(TAG, "no access unit available");
					}
				}
			} catch (Exception e) {
			}

			int outputBufferIndex = codec.dequeueOutputBuffer(info, 0);
			if(outputBufferIndex >= 0){
				if(!first){
					startWhen = System.currentTimeMillis();
					first = true;
				}
				try {
					long sleepTime = (info.presentationTimeUs / 1000)
							- (System.currentTimeMillis() - startWhen);
					Log.i(TAG, "presentation: " + info.presentationTimeUs/ 1000 + ", delay: "+ (System.currentTimeMillis()-startWhen));
					if (sleepTime > 0) {
						Thread.sleep(sleepTime);
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				codec.releaseOutputBuffer(outputBufferIndex, true);
				Log.i(TAG, "render");
			}
			
		}
		
		codec.stop();
		codec.release();
	}

	public void close() {
		isEos = true;
	}
	
	private void onConnect() {
		try {
			synchronized (mConnectionLock) {
				if (mSocket == null || !mSocket.isConnected()) {
					mSocket = new Socket();
					mSocket.connect(new InetSocketAddress("192.168.49.1", 55000));
					mSocketInputStream = new ObjectInputStream(mSocket.getInputStream());
					mSocketHandler.sendEmptyMessage(MSG_READ);
					Log.i(TAG, "onConnect and send read message");
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
			if(mQueue.size() == 29)
				mQueue.remove();
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
				//Log.i(TAG, "before read");
				onRead();
				//Log.i(TAG, "after read");
				break;
			case MSG_CLOSE:
				Log.i(TAG, "before close");
				onClose();
				Log.i(TAG, "after close");
				break;
			default:
				Log.w(TAG, "unknown message");
				break;
			}
		}
	}
	
	
}
