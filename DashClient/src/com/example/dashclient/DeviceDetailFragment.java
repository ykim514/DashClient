/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.dashclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.dashclient.DeviceListFragment.DeviceActionListener;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

	public static final String IP_SERVER = "192.168.49.1";
	public static int PORT = 55000;
	private static boolean server_running = false;

	protected static final int CHOOSE_FILE_RESULT_CODE = 20;
	private View mContentView = null;
	private WifiP2pDevice device = null;
	private WifiP2pInfo info;
	ProgressDialog progressDialog = null;

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		mContentView = inflater.inflate(R.layout.device_detail, null);
		mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				WifiP2pConfig config = new WifiP2pConfig();
				config.deviceAddress = device.deviceAddress;
				config.wps.setup = WpsInfo.PBC;
				if (progressDialog != null && progressDialog.isShowing()) {
					progressDialog.dismiss();
				}
				progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
						"Connecting to :" + device.deviceAddress, true, true
						//                        new DialogInterface.OnCancelListener() {
						//
						//                            @Override
						//                            public void onCancel(DialogInterface dialog) {
						//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
						//                            }
						//                        }
						);
				((DeviceActionListener) getActivity()).connect(config);

			}
		});

		mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						((DeviceActionListener) getActivity()).disconnect();
					}
				});

		mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						if(device == null) {
							return;
						}

						String localIP = Utils.getLocalIPAddress();
						// Trick to find the ip in the file /proc/net/arp
						String client_mac_fixed = new String(device.deviceAddress).replace("99", "19");
						String clientIP = Utils.getIPFromMac(client_mac_fixed);

						// User has picked an image. Transfer it to group owner i.e peer using
						// FileTransferService.
						TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
						Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
						serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);


						if(localIP.equals(IP_SERVER)){
							serviceIntent.putExtra(FileTransferService.EXTRAS_ADDRESS, clientIP);
							Log.i("ss","client ip1:"+clientIP);
						}else{
							serviceIntent.putExtra(FileTransferService.EXTRAS_ADDRESS, IP_SERVER);
							Log.i("ss","client ip2:"+IP_SERVER);
						}

						serviceIntent.putExtra(FileTransferService.EXTRAS_PORT, PORT);
						// getActivity().startService(serviceIntent);

						Intent intent = new Intent(getActivity(), PeerActivity.class);
						intent.putExtra(FileTransferService.EXTRAS_PORT, PORT);
						if(localIP.equals(IP_SERVER)){
							intent.putExtra(FileTransferService.EXTRAS_ADDRESS, clientIP);
							Log.i("ss","client ip1:"+clientIP);
						}else{
							intent.putExtra(FileTransferService.EXTRAS_ADDRESS, IP_SERVER);
							Log.i("ss","client ip2:"+IP_SERVER);
						}
						startActivity(intent);
					}
				});

		return mContentView;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {

	}

	@Override
	public void onConnectionInfoAvailable(final WifiP2pInfo info) {
		if (progressDialog != null && progressDialog.isShowing()) {
			progressDialog.dismiss();
		}
		this.info = info;
		this.getView().setVisibility(View.VISIBLE);

		// The owner IP is now known.
		TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
		view.setText(getResources().getString(R.string.group_owner_text)
				+ ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
						: getResources().getString(R.string.no)));

		// InetAddress from WifiP2pInfo struct.
		view = (TextView) mContentView.findViewById(R.id.device_info);
		view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

		mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);

		/*
		if (!server_running){
			new ServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text)).execute();
			server_running = true;
		}
		 */

		// hide the connect button
		mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
	}

	/**
	 * Updates the UI with device data
	 * 
	 * @param device the device to be displayed
	 */
	public void showDetails(WifiP2pDevice device) {
		this.device = device;
		this.getView().setVisibility(View.VISIBLE);
		TextView view = (TextView) mContentView.findViewById(R.id.device_address);
		view.setText(device.deviceAddress);
		view = (TextView) mContentView.findViewById(R.id.device_info);
		view.setText(device.toString());
	}

	/**
	 * Clears the UI fields after a disconnect or direct mode disable operation.
	 */
	public void resetViews() {
		mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
		TextView view = (TextView) mContentView.findViewById(R.id.device_address);
		view.setText(R.string.empty);
		view = (TextView) mContentView.findViewById(R.id.device_info);
		view.setText(R.string.empty);
		view = (TextView) mContentView.findViewById(R.id.group_owner);
		view.setText(R.string.empty);
		view = (TextView) mContentView.findViewById(R.id.status_text);
		view.setText(R.string.empty);
		mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
		this.getView().setVisibility(View.GONE);
	}


	/*
	public static class ServerAsyncTask extends AsyncTask<Void, Void, String> {

		private final Context context;
		private final TextView statusText;

		public ServerAsyncTask(Context context, View statusText) {
			this.context = context;
			this.statusText = (TextView) statusText;
		}

		@Override
		protected String doInBackground(Void... params) {
			try {
				ServerSocket serverSocket = new ServerSocket(PORT);
				Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");
				Socket client = serverSocket.accept();
				Log.d(WiFiDirectActivity.TAG, "Server: connection done");

				DataOutputStream dos = new DataOutputStream(client.getOutputStream());
				long sleepTime = 500;

				while(true) {
					Log.i("", "SendPacket");
					try {
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						Bitmap bitmap = PlayerActivity.mTextureView.getBitmap();
						bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);

						byte[] data = bos.toByteArray();
						byte[] length = ByteBuffer.allocate(4).putInt(data.length).array();
						byte[] width = ByteBuffer.allocate(4).putInt(bitmap.getWidth()).array();
						byte[] height = ByteBuffer.allocate(4).putInt(bitmap.getHeight()).array();
						byte[] packet = new byte[data.length + 12];

						System.arraycopy(length, 0, packet, 0, 4);
						System.arraycopy(width, 0, packet, 4, 4);
						System.arraycopy(height, 0, packet, 8, 4);
						System.arraycopy(data, 0, packet, 12, data.length);

						dos.write(packet);
						dos.flush();

						Thread.sleep(sleepTime);
					} catch (Exception e) {
						client.close();
						Log.e("", e.getMessage(), e);
						break;
					}
				}

				serverSocket.close();
				server_running = false;
				// return f.getAbsolutePath();
				return null;
			} catch (IOException e) {
				Log.e(WiFiDirectActivity.TAG, e.getMessage());
				return null;
			}
		}

		@Override
		protected void onPostExecute(String result) {
			if (result != null) {
				statusText.setText("File copied - " + result);
				Intent intent = new Intent();
				intent.setAction(android.content.Intent.ACTION_VIEW);
				intent.setDataAndType(Uri.parse("file://" + result), "image/*");
				context.startActivity(intent);
			}
		}

		@Override
		protected void onPreExecute() {
			statusText.setText("Opening a server socket");
		}
	}
	 */

	public static boolean copyFile(InputStream inputStream, OutputStream out) {
		byte buf[] = new byte[1024];
		int len;
		try {
			while ((len = inputStream.read(buf)) != -1) {
				out.write(buf, 0, len);

			}
			out.close();
			inputStream.close();
		} catch (IOException e) {
			Log.d(WiFiDirectActivity.TAG, e.toString());
			return false;
		}
		return true;
	}
}