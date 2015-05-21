package com.example.dashclient;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class DashHttpClient {
	public static final int MSG_SIGN_IN = 1;
	public static final int MSG_SIGN_OUT = 2;
	public static final int MSG_SIGN_UP = 3;
	public static final int MSG_MEDIA_LIST = 4;
	public static final String HOST = "http://203.252.180.194";
	private static final String NAME = DashHttpClient.class.getName();
	private HandlerThread mEventThread;
	private EventHandler mEventHandler;
	private Handler mMainHandler;
	private OnSignInListener mOnSignInListener;
	private OnSignUpListener mOnSignUpListener;
	private OnSignOutListener mOnSignOutListener;
	private OnGetMediaListListener mOnGetMediaListListener;
	private Runnable mSignInFailureAction = new Runnable() {
		@Override
		public void run() {
			if (mOnSignInListener != null) {
				mOnSignInListener.onFailure(MSG_SIGN_IN, "sign in failure");
			}
		}
	};
	private Runnable mSignUpFailureAction = new Runnable() {
		@Override
		public void run() {
			if (mOnSignUpListener != null) {
				mOnSignUpListener.onFailure(MSG_SIGN_UP, "sign up failure");
			}
		}
	};
	private Runnable mSignOutFailureAction = new Runnable() {
		@Override
		public void run() {
			if (mOnSignOutListener != null) {
				mOnSignOutListener.onFailure(MSG_SIGN_OUT, "sign out failure");
			}
		}
	};
	private Runnable mGetMediaListFailureAction = new Runnable() {
		@Override
		public void run() {
			if (mOnGetMediaListListener != null) {
				mOnGetMediaListListener.onFailure(MSG_MEDIA_LIST,
						"get media list failure");
			}
		}
	};

	public DashHttpClient() {
		mMainHandler = new Handler(Looper.getMainLooper());
		mEventThread = new HandlerThread(NAME);
		mEventThread.start();
		mEventHandler = new EventHandler(mEventThread.getLooper());
	}

	public void setOnSignInListener(OnSignInListener onSignInListener) {
		mOnSignInListener = onSignInListener;
	}

	public void setOnSignUpListener(OnSignUpListener onSignUpListener) {
		mOnSignUpListener = onSignUpListener;
	}

	public void setOnSignOutListener(OnSignOutListener onSignOutListener) {
		mOnSignOutListener = onSignOutListener;
	}

	public void setOnGetMediaListListener(
			OnGetMediaListListener onGetMediaListListener) {
		mOnGetMediaListListener = onGetMediaListListener;
	}

	public void signIn(String email, String password) {
		Map<String, String> data = new HashMap<String, String>();
		data.put("email", email);
		data.put("password", password);
		mEventHandler.obtainMessage(MSG_SIGN_IN, data).sendToTarget();
	}

	public void signUp(String email, String name, String password) {
		Map<String, String> data = new HashMap<String, String>();
		data.put("email", email);
		data.put("name", name);
		data.put("password", password);
		mEventHandler.obtainMessage(MSG_SIGN_UP, data).sendToTarget();
		;
	}

	public void signOut() {
		mEventHandler.sendEmptyMessage(MSG_SIGN_OUT);
	}

	public void getMediaList() {
		mEventHandler.sendEmptyMessage(MSG_MEDIA_LIST);
	}

	private void doSignIn(final String email, final String password) {
		if (email == null || password == null) {
			if (mOnSignInListener != null) {
				mOnSignInListener.onFailure(MSG_SIGN_IN, "parameter is null");
				return;
			}
		}
		try {
			StringBuffer buffer = new StringBuffer(HOST).append("/signIn")
					.append("?email=")
					.append(URLEncoder.encode(email, "UTF-8"))
					.append("&password=")
					.append(URLEncoder.encode(password, "UTF-8"));
			URL url = new URL(buffer.toString());
			HttpURLConnection urlConnection = (HttpURLConnection) url
					.openConnection();
			urlConnection.connect();
			switch (urlConnection.getResponseCode()) {
			case HttpURLConnection.HTTP_OK:
				mMainHandler.post(new Runnable() {
					@Override
					public void run() {
						if (mOnSignInListener != null) {
							DashCookie.setAttribute("email", email);
							mOnSignInListener.onSignIn();
						}
					}
				});
				break;
			default:
				mMainHandler.post(mSignInFailureAction);
				break;
			}
		} catch (Exception e) {
			Log.e(NAME, e.getMessage());
			mMainHandler.post(mSignInFailureAction);
		}
	}

	private void doSignUp(String email, String name, String password) {
		if (email == null || name == null || password == null) {
			if (mOnSignUpListener != null) {
				mOnSignUpListener.onFailure(MSG_SIGN_UP, "parameter is null");
				return;
			}
		}
		try {
			StringBuffer buffer = new StringBuffer(HOST).append("/signUp")
					.append("?email=")
					.append(URLEncoder.encode(email, "UTF-8"))
					.append("&password=")
					.append(URLEncoder.encode(password, "UTF-8"))
					.append("&name=").append(URLEncoder.encode(name, "UTF-8"));
			URL url = new URL(buffer.toString());
			HttpURLConnection urlConnection = (HttpURLConnection) url
					.openConnection();
			urlConnection.connect();
			switch (urlConnection.getResponseCode()) {
			case HttpURLConnection.HTTP_OK:
				mMainHandler.post(new Runnable() {
					@Override
					public void run() {
						if (mOnSignUpListener != null) {
							mOnSignUpListener.onSignUp();
						}
					}
				});
				break;
			default:
				mMainHandler.post(mSignUpFailureAction);
				break;
			}
		} catch (Exception e) {
			Log.e(NAME, e.getMessage());
			mMainHandler.post(mSignUpFailureAction);
		}
	}

	private void doSignOut() {
		try {
			StringBuffer buffer = new StringBuffer(HOST)
					.append("/signOut")
					.append("?email=")
					.append(URLEncoder.encode(DashCookie.getAttribute("email"),
							"UTF-8"));
			URL url = new URL(buffer.toString());
			HttpURLConnection urlConnection = (HttpURLConnection) url
					.openConnection();
			urlConnection.connect();
			switch (urlConnection.getResponseCode()) {
			case HttpURLConnection.HTTP_OK:
				mMainHandler.post(new Runnable() {
					@Override
					public void run() {
						if (mOnSignOutListener != null) {
							mOnSignOutListener.onSignOut();
						}
					}
				});
				break;
			default:
				mMainHandler.post(mSignOutFailureAction);
				break;
			}
		} catch (Exception e) {
			Log.e(NAME, e.getMessage());
			mMainHandler.post(mSignOutFailureAction);
		}
	}

	private void doGetMediaList() {
		try {
			StringBuffer buffer = new StringBuffer(HOST).append("/medias");
			URL url = new URL(buffer.toString());
			final HttpURLConnection urlConnection = (HttpURLConnection) url
					.openConnection();
			urlConnection.connect();
			switch (urlConnection.getResponseCode()) {
			case HttpURLConnection.HTTP_OK:
				final JSONArray medias = new JSONArray(
						readStream(urlConnection.getInputStream()));
				mMainHandler.post(new Runnable() {
					@Override
					public void run() {
						if (mOnGetMediaListListener != null) {
							try {
								List<DashMedia> itemList = new ArrayList<DashMedia>();
								for (int i = 0; i < medias.length(); i++) {
									JSONArray media = medias.getJSONArray(i);
									DashMedia item = new DashMedia();
									item.setId(media.getInt(0));
									item.setName(URLDecoder.decode(
											media.getString(1), "UTF-8"));
									item.setSize(media.getInt(2));
									item.setPath(media.getString(3));
									itemList.add(item);
								}
								mOnGetMediaListListener
										.onGetMediaList(itemList);
							} catch (Exception e) {
								Log.e(NAME, e.getMessage(), e);
							}
						}
					}
				});
				break;
			default:
				mMainHandler.post(mGetMediaListFailureAction);
				break;
			}
		} catch (Exception e) {
			Log.e(NAME, e.getMessage());
			mMainHandler.post(mGetMediaListFailureAction);
		}
	}

	private String readStream(InputStream in) throws IOException {
		StringBuffer sb = new StringBuffer();
		byte[] buf = new byte[4096];
		int len;
		while ((len = in.read(buf)) > 0) {
			sb.append(new String(buf, 0, len));
		}
		return sb.toString();
	}

	public interface OnSignInListener extends OnFailureListener {
		public void onSignIn();
	}

	public interface OnSignUpListener extends OnFailureListener {
		public void onSignUp();
	}

	public interface OnSignOutListener extends OnFailureListener {
		public void onSignOut();
	}

	public interface OnGetMediaListListener extends OnFailureListener {
		public void onGetMediaList(List<DashMedia> mediaList);
	}

	private interface OnFailureListener {
		public void onFailure(int event, String message);
	}

	private class EventHandler extends Handler {
		public EventHandler(Looper looper) {
			super(looper);
		}

		@Override
		@SuppressWarnings("unchecked")
		public void handleMessage(Message msg) {
			Map<String, String> data = null;
			switch (msg.what) {
			case MSG_SIGN_IN:
				data = (Map<String, String>) msg.obj;
				doSignIn(data.get("email"), data.get("password"));
				break;
			case MSG_SIGN_UP:
				data = (Map<String, String>) msg.obj;
				doSignUp(data.get("email"), data.get("name"),
						data.get("password"));
				break;
			case MSG_SIGN_OUT:
				doSignOut();
				break;
			case MSG_MEDIA_LIST:
				doGetMediaList();
				break;
			default:
				Log.w(NAME, "unknown message");
				break;
			}
		}
	}
}