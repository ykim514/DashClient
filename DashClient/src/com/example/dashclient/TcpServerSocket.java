package com.example.dashclient;

import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class TcpServerSocket {

	private static final String TAG = TcpServerSocket.class.getName();
	private static final int MSG_ACCEPT = 1;
	private static final int MSG_READ = 2;
	private static final int MSG_WRITE = 3;
	private static final int MSG_CLOSE = 4;
	private ServerSocket mServerSocket;
	private Socket mSocket;
	private HandlerThread mEventThread;
	private EventHandler mEventHandler;

	public void init() {
		mEventThread = new HandlerThread(TAG);
		mEventThread.start();
		mEventHandler = new EventHandler(mEventThread.getLooper());

		try {
			mServerSocket = new ServerSocket(55000, 1);
			mEventHandler.sendEmptyMessage(MSG_ACCEPT);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public void write(byte[] data) {
		byte[] packet = new byte[data.length + 4];
		byte[] length = ByteBuffer.allocate(4).putInt(data.length).array();

		System.arraycopy(length, 0, packet, 0, 4);
		System.arraycopy(data, 0, packet, 4, data.length);

		mEventHandler.obtainMessage(MSG_WRITE, packet).sendToTarget();;
	}

	public void close() {
		mEventHandler.sendEmptyMessage(MSG_CLOSE);
	}

	private void onAccept(ServerSocket serverSocket) {
		try {
			mSocket = mServerSocket.accept();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);

			mEventHandler.sendEmptyMessage(MSG_CLOSE);
		}
	}

	private void onWrite(Socket socket, byte[] data) {
		try {
			DataOutputStream dos = new DataOutputStream(mSocket.getOutputStream());
			dos.write(data);
			dos.flush();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void onClose(Socket socket) {
		try {
			if (socket != null) {
				socket.close();
			}

			mEventHandler.sendEmptyMessage(MSG_ACCEPT);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private class EventHandler extends Handler {

		public EventHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_ACCEPT:
				Log.i(TAG, "before onAccept");
				onAccept(mServerSocket);
				Log.i(TAG, "after onAccept");
				break;
			case MSG_READ:
				// TODO nothing
				break;
			case MSG_WRITE:
				Log.i(TAG, "before onWrite");

				if(mSocket != null && mSocket.isConnected()) {
					onWrite(mSocket, (byte[])msg.obj);
				}

				Log.i(TAG, "after onWrite");
				break;
			case MSG_CLOSE:
				Log.i(TAG, "before onClose");

				if(mSocket != null && mSocket.isConnected()) {
					onClose(mSocket);
				}

				Log.i(TAG, "after onClose");
			default:
				Log.w(TAG, "unknown message");
				break;
			}
		}
	}
}
