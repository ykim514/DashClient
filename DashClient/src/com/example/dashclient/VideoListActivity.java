package com.example.dashclient;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

public class VideoListActivity extends Activity implements SocketHandler {

	private static final String TAG = VideoListActivity.class.getName();
	ArrayList<VideoInfo> videoList = new ArrayList<VideoInfo>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_videolist);
		
		init();
		getList();
		initList();
		
		SocketService.addActivity(getName(), this);
		SocketService.addHandler(this);
		SocketService.getInstance().getMediaList();
	}
	
	void init(){
		
	}
	
	void getList(){
		videoList.add(new VideoInfo("[MV] F(x) - 첫사랑니","http://211.189.19.23:4389/static/1430291280068.mpd"));
		videoList.add(new VideoInfo("[MV] EXO - Call me baby","http://211.189.19.23:4389/static/exo.mpd"));
		videoList.add(new VideoInfo("[Test] Big bunny","http://211.189.19.23:4389/static/test.mpd"));
		videoList.add(new VideoInfo("Dash Test","http://yt-dash-mse-test.commondatastorage.googleapis.com/media/car-20120827-manifest.mpd"));
	}
	void initList(){
		VideoAdapter adapter;
		adapter = new VideoAdapter(this, R.layout.item, videoList);
		ListView list = (ListView)findViewById(R.id.list);
		list.setAdapter(adapter);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id){
				Intent intent = new Intent(VideoListActivity.this, PlayerActivity.class);
				intent.putExtra("address", videoList.get(position).mAddress);
				startActivity(intent);
			}
				});
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

	@Override
	public String getName() {
		return "mediaList";
	}

	@Override
	public void onSuccess() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSuccess(Object... args) {
		getList();
		try {
			JSONArray array = (JSONArray) args[0];
			
			for(int i = 0; i < array.length(); i++) {
				JSONObject json = array.getJSONObject(i);
				
				videoList.add(new VideoInfo(json.getString("media_nm"), "http://211.189.19.23:4389/static/" + json.getString("media_path")));
			}
		} catch(Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	@Override
	public void onFailure(String message) {
		Toast.makeText(getApplicationContext(), "미디어 리스트를 받아오는 도중 오류가 발생하였습니다.", Toast.LENGTH_SHORT).show();
	}
}
