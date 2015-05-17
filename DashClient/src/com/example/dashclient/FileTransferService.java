package com.example.dashclient;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class FileTransferService extends IntentService {

	private static final String TAG = FileTransferService.class.getName();
	private static final int SOCKET_TIMEOUT = 5000;

	public static final String ACTION_SEND_FILE = "com.example.dashclient.SEND_FILE";
	public static final String EXTRAS_FILE_PATH = "file_url";
	public static final String EXTRAS_ADDRESS = "go_host";
	public static final String EXTRAS_PORT = "go_port";

	public FileTransferService(String name) {
		super(name);
	}

	public FileTransferService() {
		super("FileTransferService");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("ss","onStartCommand");
		return super.onStartCommand(intent, flags, startId);
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.IntentService#onHandleIntent(android.content.Intent)
	 */
	@Override
	protected void onHandleIntent(Intent intent) {
		Log.i("ss","onHandleIntent");
		Context context = getApplicationContext();
		if (intent.getAction().equals(ACTION_SEND_FILE)) {
			String host = intent.getExtras().getString(EXTRAS_ADDRESS);
			Socket socket = new Socket();

			try {
				Log.d(WiFiDirectActivity.TAG, "Opening client socket - ");
				socket.connect((new InetSocketAddress(host, 55000)), SOCKET_TIMEOUT);
				Log.d(WiFiDirectActivity.TAG, "Client socket - " + socket.isConnected());

				Log.i(TAG, "isClosed: " + socket.isClosed());
				Log.i(TAG, "isConnected: " + socket.isConnected());
				Log.i(TAG, "isBound: " + socket.isBound());
				Log.i(TAG, "isInputShutdown: " + socket.isInputShutdown());
				Log.i(TAG, "isOutputShutdown: " + socket.isOutputShutdown());
				DataInputStream dis = new DataInputStream(socket.getInputStream());

				while (true) {
					byte[] buf = new byte[4096];
					int len = dis.read(buf);

					Log.i(TAG, "" + len);

					byte[] bDataLength = getByteArray(dis, 4);

					if(bDataLength == null) {
						break;
					}

					int dataLength = ByteBuffer.wrap(bDataLength).getInt();
					byte[] data = getByteArray(dis, dataLength);

					if(data == null) {
						break;
					}

					int width = ByteBuffer.wrap(Arrays.copyOfRange(data, 0, 4)).getInt();
					int height = ByteBuffer.wrap(Arrays.copyOfRange(data, 4, 8)).getInt();
					byte[] bitmap = ByteBuffer.wrap(Arrays.copyOfRange(data, 8, data.length)).array();

					Log.i("ss", "ReceivePacket");

					//PeerActivity.mEventHandler.obtainMessage(PeerActivity.NOTIFY_UPDATE_VIEW, width, height, bitmap);
				}

				/*
				OutputStream stream = socket.getOutputStream();
				ContentResolver cr = context.getContentResolver();
				InputStream is = null;
				try {
					is = cr.openInputStream(Uri.parse(fileUri));
				} catch (FileNotFoundException e) {
					Log.d(WiFiDirectActivity.TAG, e.toString());
				}
				DeviceDetailFragment.copyFile(is, stream);
				Log.d(WiFiDirectActivity.TAG, "Client: Data written");
				 */
			} catch (IOException e) {
				Log.e(WiFiDirectActivity.TAG, e.getMessage());
			} finally {
				if (socket != null) {
					if (socket.isConnected()) {
						try { 
							socket.close();
						} catch (IOException e) {
							// Give up
							e.printStackTrace();
						}
					}
				}
			}
		}
	}

	public byte[] getByteArray(InputStream is, int totalLength) {
		byte[] buf = new byte[4096];
		int remainder = totalLength;

		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		while (remainder != 0) {
			try {
				int len = is.read(buf, 0, (remainder < 4096 ? remainder : 4096));
				Log.i("TAG", "length: " + len);
				Log.i("TAG", "remainder: " + remainder);

				bos.write(buf, 0, len);
				remainder -= len;
			} catch (IOException e) {
				Log.e("", e.getMessage(), e);
				return null;
			}
		}

		return bos.toByteArray();
	}
}