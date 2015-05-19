package com.example.dashclient;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class VideoAdapter extends BaseAdapter{

	Context mContext;
	LayoutInflater inflater;
	ArrayList<DashMedia> mList;
	int mLayout;
	
	public VideoAdapter(Context context, int layout, ArrayList<DashMedia> aV){
		mContext = context;
		inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mList = aV;
		mLayout = layout;
	}
				
	@Override
	public int getCount() {
		return mList.size();
	}

	@Override
	public Object getItem(int position) {
		return mList.get(position).getName();
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if(convertView == null){
			convertView = inflater.inflate(mLayout, parent, false);
		}
		TextView txt = (TextView)convertView.findViewById(R.id.txt);
		txt.setText(mList.get(position).getName());
		return convertView;
	}

}
