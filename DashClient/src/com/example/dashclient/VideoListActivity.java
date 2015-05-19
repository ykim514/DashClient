package com.example.dashclient;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.example.dashclient.DashHttpClient.OnGetMediaListListener;

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

public class VideoListActivity extends Activity implements OnGetMediaListListener {

	private static final String TAG = VideoListActivity.class.getName();
	ArrayList<DashMedia> videoList = new ArrayList<DashMedia>();
	DashHttpClient mDashHttpClient;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_videolist);
		
		init();
		initList();
	}
	
	void init(){
		mDashHttpClient = new DashHttpClient();
		mDashHttpClient.setOnGetMediaListListener(this);
		mDashHttpClient.getMediaList();
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
				intent.putExtra("address", videoList.get(position).getPath());
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
	public void onFailure(int event, String message) {
		 Toast.makeText(this, String.format("event: %d, %s", event, message), Toast.LENGTH_SHORT).show();
		
	}

	@Override
	public void onGetMediaList(List<DashMedia> mediaList) {
		videoList.add(new DashMedia(0,"[MV] EXO - call me baby", 40, "http://211.189.19.23:4389/static/exo.mpd"));
		for(DashMedia media : mediaList){
			videoList.add(media);
		}
		
	}
}
