package com.example.dashclient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class TcpClientSocket {

	private static final String TAG = TcpServerSocket.class.getName();
	private static final int MSG_CONNECT = 1;
	private static final int MSG_READ = 2;
	private static final int MSG_WRITE = 3;
	private static final int MSG_CLOSE = 4;
	private Socket mSocket;
	private HandlerThread mEventThread;
	private EventHandler mEventHandler;
	private Object mSocketLock = new Object();

	public void init() {
		mEventThread = new HandlerThread(TAG);
		mEventThread.start();

		mEventHandler = new EventHandler(mEventThread.getLooper());
	}

	public void connect() {
		mEventHandler.sendEmptyMessage(MSG_CONNECT);
	}

	private void onConnect(Socket socket) {
		try {
			synchronized (mSocketLock) {
				if (socket == null || socket.isClosed()) {
					socket = new Socket();
				}
				socket.connect(new InetSocketAddress("127.0.0.1", 55000));
			}

			mEventHandler.sendEmptyMessage(MSG_READ);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);

			try {
				Thread.sleep(1000);
			} catch (Exception e2) {}

			mEventHandler.sendEmptyMessage(MSG_CONNECT);
		}
	}

	private void onRead(Socket socket) {
		try {
			synchronized (mSocketLock) {
				DataInputStream dis = new DataInputStream(socket.getInputStream());
				String value = dis.readUTF();
				Log.i(TAG, value);
			}

			mEventHandler.sendEmptyMessage(MSG_CLOSE);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);

			mEventHandler.sendEmptyMessage(MSG_CLOSE);
		}
	}

	private void onWrite(Socket socket) {
		try {
			synchronized (mSocketLock) {
				DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
				dos.writeUTF("Hello, world!");
				dos.flush();
			}

			mEventHandler.sendEmptyMessage(MSG_CLOSE);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void onClose(Socket socket) {
		try {
			synchronized (mSocketLock) {
				if (socket != null) {
					socket.close();
					socket = null;
				}
			}

			mEventHandler.sendEmptyMessage(MSG_CONNECT);
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
			case MSG_CONNECT:
				Log.i(TAG, "before onConnect");
				onConnect(mSocket);
				Log.i(TAG, "after onConnect");
				break;
			case MSG_READ:
				Log.i(TAG, "before onRead");
				onRead(mSocket);
				Log.i(TAG, "after onRead");
				break;
			case MSG_WRITE:
				Log.i(TAG, "before onWrite");
				onWrite(mSocket);
				Log.i(TAG, "after onWrite");
				break;
			case MSG_CLOSE:
				Log.i(TAG, "before onClose");
				onClose(mSocket);
				Log.i(TAG, "after onClose");
			default:
				Log.w(TAG, "unknown message");
				break;
			}
		}
	}
}
