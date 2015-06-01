package com.example.dashclient;

import java.io.IOException;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;




public class PeerActivity extends Activity implements TextureView.SurfaceTextureListener{

	public static final int NOTIFY_UPDATE_VIEW = 1;
	public static TextureView mTextureView;
	private static final String TAG = PeerActivity.class.getName();
	private VideoDecodeThread mVideoDecodeThread;
	private AudioDecodeThread mAudioDecodeThread;

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
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 
		
		mAudioDecodeThread = new AudioDecodeThread();
	}
	
	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
		mVideoDecodeThread = new VideoDecodeThread(new Surface(surface));
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
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		if (mVideoDecodeThread != null) {
			mVideoDecodeThread.onClose();
			mVideoDecodeThread.close();
			mVideoDecodeThread = null;
		}
		if(mAudioDecodeThread != null){
			mAudioDecodeThread.onClose();
			mAudioDecodeThread.close();
			mAudioDecodeThread = null;
		}
		super.onDestroy();
	}
	 

}