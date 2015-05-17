package com.example.dashclient;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.util.Log;

public class SocketService {

	private static final String TAG = SocketService.class.getSimpleName();
	private static SocketService instance = new SocketService();
	private SocketIO socket; 
	private SocketCallback callback = new SocketCallback();
	private static Map<String, SocketHandler> handlers = new HashMap<String, SocketHandler>();
	private static Map<String, Activity> activitys = new HashMap<String, Activity>();

	public static void addHandler(SocketHandler handler) {
		handlers.put(handler.getName(), handler);
	}

	public static void addActivity(String name, Activity activity) {
		activitys.put(name, activity);
	}

	public void init() {
		try {
			socket = new SocketIO("http://211.189.19.23:12323/");
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		socket.connect(callback);

		// This line is cached until the connection is establisched.
		socket.send("Hello Server!");
	}

	public static SocketService getInstance(){
		return instance;
	}

	public void signIn(String email, String password) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("email", email);
		param.put("password", password);
		sendMessage("signIn", param);
	}

	public void signUp(String email, String password, String name) {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("email", email);
		param.put("password", password);
		param.put("name", name);
		sendMessage("signUp", param);
	}

	public void signOut(){
		sendMessage("signOut");
	}

	public void getMediaList() {
		sendMessage("mediaList");
	}

	private void sendMessage(String event){
		sendMessage(event, null);
	}

	private void sendMessage(String event, Map<String, Object> param) {
		if((socket == null) || !socket.isConnected()) {
			Log.e(TAG, "시그널링 서버와 연결되지 않았습니다.");
			return;
		}
		if(param == null) {
			socket.emit(event);
			return;
		}
		JSONObject json = new JSONObject();
		try {
			for(String key : param.keySet()) {
				json.put(key, param.get(key));
			}
			socket.emit(event, json);
		} catch(Exception e) {
			Log.e(TAG, e.getMessage(), e);
			try {
				json.put("ret", 1);
			} catch(Exception ex) {}
			callback.on(event, null, json);
		}
	}

	private static class SocketCallback implements IOCallback{

		@Override
		public void onMessage(JSONObject json, IOAcknowledge ack) {
			try {
				System.out.println("Server said:" + json.toString(2));
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onMessage(String data, IOAcknowledge ack) {
			System.out.println("Server said: " + data);
		}

		@Override
		public void onError(SocketIOException socketIOException) {
			System.out.println("an Error occured");
			socketIOException.printStackTrace();
		}

		@Override
		public void onDisconnect() {
			System.out.println("Connection terminated.");
		}

		@Override
		public void onConnect() {
			System.out.println("Connection established");
		}

		@Override
		public void on(String event, IOAcknowledge ack, final Object... args) {
			System.out.println("Server triggered event '" + event + "'");

			final SocketHandler handler = handlers.get(event);

			if(handler == null) {
				Log.i(TAG, "handler is not registerd");
				return;
			}

			Activity activity = activitys.get(event);

			if("signIn".equals(event)) {
				if(args[0] != null || args[1] == null) {
					if(activity == null) {
						handler.onFailure((String)args[0]);
					} else {
						activity.runOnUiThread(new Runnable() {
							public void run() {
								handler.onFailure((String)args[0]);
							}
						});
					}
				} else {
					if(activity == null) {
						handler.onSuccess();
					} else {
						activity.runOnUiThread(new Runnable() {
							public void run() {
								handler.onSuccess();
							}
						});
					}
				}
			} else if("signUp".equals(event)) {
				if(args[0] != null || args[1] == null) {
					if(activity == null) {
						handler.onFailure((String)args[0]);
					} else {
						activity.runOnUiThread(new Runnable() {
							public void run() {
								handler.onFailure((String)args[0]);
							}
						});
					}
				} else {
					if(activity == null) {
						handler.onSuccess();
					} else {
						activity.runOnUiThread(new Runnable() {
							public void run() {
								handler.onSuccess();
							}
						});
					}
				}
			} else if("signOut".equals(event)) {
				if(args.length > 1 && args[0] != null) {
					handler.onFailure((String)args[0]);
				} else {
					handler.onSuccess();
				}
			} else if("mediaList".equals(event)) {
				if(args.length > 1 && args[0] != null) {
					handler.onFailure((String)args[0]);
				} else {
					handler.onSuccess((JSONArray)args[1]);
				}
			} else {
				Log.w(TAG, "Unknown event");
			}
		}
	}

}
