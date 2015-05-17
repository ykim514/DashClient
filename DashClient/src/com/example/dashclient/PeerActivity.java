package com.example.dashclient;

import java.io.IOException;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

//import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.peer.MediaPlayer;



public class PeerActivity extends Activity implements TextureView.SurfaceTextureListener{

	public static final int NOTIFY_UPDATE_VIEW = 1;
	public static TextureView mTextureView;
	private static final String TAG = PeerActivity.class.getName();
	
	MediaPlayer mMediaPlayer;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_peer);

		init();
	}

	private void init() {
		mTextureView = (TextureView)findViewById(R.id.mPeerView);
		mTextureView.setSurfaceTextureListener(this);
		mTextureView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
		
		mMediaPlayer = new MediaPlayer();
		try {
			mMediaPlayer.setDataSource("http://211.189.19.23:4389/static/exo.mpd");
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		mMediaPlayer.prepare();
		mMediaPlayer.play();
	}
	
	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
		mMediaPlayer.setDisplay(new Surface(surface));
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
		return false;
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture surface) {

	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

	@Override
	protected void onPause() {
		if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
			mMediaPlayer.pause();
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		if (mMediaPlayer != null) {
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
		super.onDestroy();
	}
	 

}