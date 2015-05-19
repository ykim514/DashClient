package com.example.dashclient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.sonymobile.seeder.MediaPlayer;

public class PlayerActivity extends Activity implements SurfaceHolder.Callback, 
		OnTouchListener, View.OnSystemUiVisibilityChangeListener, 
		MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnCompletionListener,
		MediaPlayer.OnErrorListener{

	ImageView mPlayBtn;
	SeekBar mSeekBar;
	MediaPlayer mMediaPlayer;
	SurfaceView mSurfaceview;
	RelativeLayout mPlayerLayout;
	View mDecorView;
	TextView mTimeView, mDurationView;
	LinearLayout mTimeLayout;
	SurfaceHolder mHolder;
	Context wContext;
	Runnable mHideRunnable;
	Runnable mSeekbarRunnable;
	Runnable mTimeRunnable;
	Handler mHandler;
	String MPDaddress;
	boolean visible = true;
	boolean mSeekLock = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_player);
		
		Intent intent = getIntent();
		MPDaddress = intent.getStringExtra("address");
		Log.i("dd",MPDaddress);

		init();
	}

	void init() {
		mDecorView = getWindow().getDecorView();
		mDecorView.setOnSystemUiVisibilityChangeListener(this);
		mPlayBtn = (ImageView) findViewById(R.id.playBtn);
		//mWidiSetBtn = (ImageView)findViewById(R.id.widisetBtn);
		mSeekBar = (SeekBar)findViewById(R.id.seekbar);
		mSurfaceview = (SurfaceView) findViewById(R.id.mSurfaceView);
		mSurfaceview.setOnTouchListener(this);
		mHolder = mSurfaceview.getHolder();
		mHolder.addCallback(this);
		mTimeView = (TextView)findViewById(R.id.seektimeTxt);
		mDurationView = (TextView)findViewById(R.id.totaltimeTxt);
		mPlayerLayout = (RelativeLayout)findViewById(R.id.playerLayout);
		mTimeLayout = (LinearLayout)findViewById(R.id.timeTextLayout);
		
		mSurfaceview.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
		mPlayerLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 

		
		mHandler = new Handler();

		mMediaPlayer = new MediaPlayer();
		try {
			mMediaPlayer.setDataSource(MPDaddress);
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		mHideRunnable = new Runnable() {

			@Override
			public void run() {
				setInvisible();
			}

		};

		mSeekbarRunnable = new Runnable() {
			@Override
			public void run() {
				int currentPosition = mMediaPlayer.getCurrentPosition();
				if (currentPosition > 0) {
					int position = (int) (((double) currentPosition / mMediaPlayer
							.getDuration()) * 1000);
					mSeekBar.setProgress(position);
				}
				if(mMediaPlayer.getState()==MediaPlayer.State.PLAYING)
					mHandler.postDelayed(mSeekbarRunnable, 100);
			}
		};
		
		mMediaPlayer.setDisplay(mHolder);
		mMediaPlayer.setOnSeekCompleteListener(this);
		mMediaPlayer.setOnCompletionListener(this);
		mSeekBar.setOnSeekBarChangeListener(new OwnOnSeekBarChangeListener(mMediaPlayer));
		mMediaPlayer.prepare();
		mMediaPlayer.play();
		mTimeRunnable = new Runnable() {

			@Override
			public void run() {
				int position = mMediaPlayer.getCurrentPosition();
				if (position > 0) {
					mTimeView.setText(String.format(
							"%02d:%02d:%02d",
							TimeUnit.MILLISECONDS.toHours(position),
							TimeUnit.MILLISECONDS.toMinutes(position)
									- TimeUnit.HOURS
											.toMinutes(TimeUnit.MILLISECONDS
													.toHours(position)),
							TimeUnit.MILLISECONDS.toSeconds(position)
									- TimeUnit.MINUTES
											.toSeconds(TimeUnit.MILLISECONDS
													.toMinutes(position))));

				}
				if (position >= 0)
					mHandler.postDelayed(mTimeRunnable, 1000);
			}
		};
		setTimeOnView(mDurationView, mMediaPlayer.getDuration());
		mHandler.postDelayed(mHideRunnable, 4000);
		if(mMediaPlayer.getState()==MediaPlayer.State.PLAYING){
			mHandler.post(mSeekbarRunnable);
			mHandler.post(mTimeRunnable);
			mPlayBtn.setBackground(getResources().getDrawable(R.drawable.pause));
		}

		
		
	}
	
	void setVisible(){
		mSeekBar.setVisibility(View.VISIBLE);
		mPlayBtn.setVisibility(View.VISIBLE);
		//mWidiSetBtn.setVisibility(View.VISIBLE);
		mTimeLayout.setVisibility(View.VISIBLE);
		visible = true;
	}
	
	void setInvisible(){
		mSeekBar.setVisibility(View.GONE);
		mPlayBtn.setVisibility(View.GONE);
		//mWidiSetBtn.setVisibility(View.GONE);
		mTimeLayout.setVisibility(View.GONE);
		visible = false;
		mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
	            | View.SYSTEM_UI_FLAG_FULLSCREEN);
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
		mHandler.removeCallbacks(mSeekbarRunnable);
		mHandler.removeCallbacks(mTimeRunnable);
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		mHandler.removeCallbacks(mSeekbarRunnable);
		mHandler.removeCallbacks(mTimeRunnable);
		if (mMediaPlayer != null) {
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void mOnClick(View v) {
		if (v.getId() == mPlayBtn.getId()) {
			if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
				mMediaPlayer.pause();
				mPlayBtn.setBackground(getResources().getDrawable(R.drawable.play));
				mHandler.removeCallbacks(mSeekbarRunnable);
				mHandler.removeCallbacks(mTimeRunnable);
			} else {
				mMediaPlayer.play();
				mHandler.postDelayed(mSeekbarRunnable, 100);
				mHandler.postDelayed(mTimeRunnable, 1000);
				mPlayBtn.setBackground(getResources().getDrawable(R.drawable.pause));
			}
		}
//		else if(v.getId() == mWidiSetBtn.getId()){
//			mOnClick(mPlayBtn);
//			Intent intent = new Intent(PlayerActivity.this, WiFiDirectActivity.class);
//			startActivity(intent);
//		}

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (mMediaPlayer != null) {
			// mSeekBarUpdater.deactivate();
			// mTimeTracker.stopUpdating();
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if(mSeekBar.getVisibility()==View.GONE){
			setVisible();
			mHandler.postDelayed(mHideRunnable, 4000);
		}
		else{
			mHandler.removeCallbacks(mHideRunnable);
			mHandler.postDelayed(mHideRunnable, 4000);
		}
		return false;
	}

	@Override
	public void onSystemUiVisibilityChange(int visibility) {
		if(visibility==6){
			mHandler.postDelayed(mHideRunnable, 4000);
		}
		else{
			setVisible();
		}
		visible = !visible;
	}

	@Override
	public void onSeekComplete(MediaPlayer arg0) {
		mHandler.postDelayed(mSeekbarRunnable, 100);
		setTimeOnView(mTimeView, mMediaPlayer.getCurrentPosition());

		if (!mSeekLock) {
			mOnClick(mPlayBtn);
		}

	}
	
	private class OwnOnSeekBarChangeListener implements OnSeekBarChangeListener {
		private int progressFromUser = 0;

		private MediaPlayer mCurrentMediaPlayer;

		public OwnOnSeekBarChangeListener(MediaPlayer mMediaPlayer) {
			mCurrentMediaPlayer = mMediaPlayer;
		}

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			seekBar.setProgress(progress);
			if (fromUser) {
				progressFromUser = progress;
				int time = (int) (mCurrentMediaPlayer.getDuration() * ((double) progress / 1000));
				setTimeOnView(mTimeView, time);
				mHandler.removeCallbacks(mHideRunnable);
				if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
					mOnClick(mPlayBtn);
				}
				mCurrentMediaPlayer.seekTo((int) ((mCurrentMediaPlayer
						.getDuration() * ((double) progressFromUser / 1000))));
			}
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {

			mHandler.removeCallbacks(mHideRunnable);
			if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
				mOnClick(mPlayBtn);
			}
			mSeekLock = true;
			mPlayBtn.setVisibility(View.GONE);
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {

			mCurrentMediaPlayer.seekTo((int) ((mCurrentMediaPlayer
					.getDuration() * ((double) progressFromUser / 1000))));
			mHandler.postDelayed(mHideRunnable, 3500);
			mSeekLock = false;
			mPlayBtn.setVisibility(View.VISIBLE);
		}
	}
	 
	 private void setTimeOnView(TextView tv, int time) {
	        tv.setText(String.format("%02d:%02d:%02d",
	                TimeUnit.MILLISECONDS.toHours(time), TimeUnit.MILLISECONDS.toMinutes(time)
	                        - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time)),
	                TimeUnit.MILLISECONDS.toSeconds(time)
	                        - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))));
	    }

	@Override
	public void onCompletion(MediaPlayer arg0) {
		mHandler.removeCallbacks(mSeekbarRunnable);
		mHandler.removeCallbacks(mTimeRunnable);
		setTimeOnView(mTimeView, mMediaPlayer.getDuration());
		mSeekBar.setVisibility(View.VISIBLE);
	   mTimeLayout.setVisibility(View.VISIBLE);
	   mPlayBtn.setVisibility(View.VISIBLE);
	   //mWidiSetBtn.setVisibility(View.VISIBLE);
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Toast.makeText(getApplicationContext(), "Error",
                Toast.LENGTH_LONG).show();
		mHandler.removeCallbacks(mSeekbarRunnable);
		mHandler.removeCallbacks(mTimeRunnable);
		return false;
	}

}

