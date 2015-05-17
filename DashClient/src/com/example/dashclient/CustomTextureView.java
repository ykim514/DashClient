package com.example.dashclient;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;

public class CustomTextureView extends TextureView implements SurfaceTextureListener {

	private Context mContext;
	private boolean isRunning = false;
	private SurfaceTexture mSurface;

	public CustomTextureView (Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
		setFocusableInTouchMode(true);
		setFocusable(true);
		setSurfaceTextureListener(this);
	}

	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,
			int height) {
		// TODO Auto-generated method stub
		isRunning = true;
		mSurface = surface;
		invalidate();//this is essential to make it display on 1st run..why?
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
		return false;
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture surface) {

	}
}