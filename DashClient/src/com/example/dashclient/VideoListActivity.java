package com.example.dashclient;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.example.dashclient.DashHttpClient.OnGetMediaListListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
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
	int ListPosition;
	
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
		// 일단 하나 박아둠
		videoList.add(new DashMedia(0,"[MV] EXO - call me baby", 40, "http://211.189.19.23:4389/static/exo.mpd"));
		mDashHttpClient.getMediaList();
		
		
	}
	
	public AlertDialog createDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder = new AlertDialog.Builder(this);
		builder.setTitle("Wifi Direct");
		builder.setMessage("Wifi Direct가 연결중입니다.\n상대 기기에서 먼저 화면을 띄운 후 확인 버튼을 눌러주세요.");
		builder.setPositiveButton("확인", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Intent intent = new Intent(VideoListActivity.this, PlayerActivity.class);
				intent.putExtra("address", videoList.get(ListPosition).getPath());
				startActivity(intent);
			}
		});
		builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {}
		});
		
		AlertDialog dialog = builder.create();
		return dialog;
	}
	
	void initList(){
		VideoAdapter adapter;
		adapter = new VideoAdapter(this, R.layout.item, videoList);
		ListView list = (ListView)findViewById(R.id.list);
		list.setAdapter(adapter);
		
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,	int position, long id){
				ListPosition = position;
				if(DeviceListFragment.WidiStatus == WifiP2pDevice.CONNECTED){
					AlertDialog dialog = createDialog();
					dialog.show();
				}else{
					Intent intent = new Intent(VideoListActivity.this, PlayerActivity.class);
					intent.putExtra("address", videoList.get(ListPosition).getPath());
					startActivity(intent);
				}
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
		//videoList.add(new DashMedia(0,"[MV] EXO - call me baby", 40, "http://211.189.19.23:4389/static/exo.mpd"));
		for(DashMedia media : mediaList){
			videoList.add(media);
		}
		
	}
}
