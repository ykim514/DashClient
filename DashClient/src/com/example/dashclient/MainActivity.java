package com.example.dashclient;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

	Button VideoListBtn, FindSeederBtn, GetSeederBtn;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		init();
	}
	
	void init(){
		VideoListBtn = (Button)findViewById(R.id.listBtn);
		FindSeederBtn = (Button)findViewById(R.id.seederBtn);
		GetSeederBtn = (Button)findViewById(R.id.peerBtn);
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
	
	public void mOnClick(View v){
		if(v.getId()==VideoListBtn.getId()){
			Intent intent = new Intent(MainActivity.this, VideoListActivity.class);
			startActivity(intent);
		}
		else if(v.getId()==FindSeederBtn.getId()){
			Intent intent = new Intent(MainActivity.this, WiFiDirectActivity.class);
			startActivity(intent);
		}
		else if(v.getId()==GetSeederBtn.getId()){
			Intent intent = new Intent(MainActivity.this, PeerActivity.class);
			startActivity(intent);
		}
	}
}
