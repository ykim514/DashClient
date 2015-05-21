package com.example.dashclient;

import java.util.HashMap;
import java.util.Map;

public class DashCookie {
	private static Map<String, String> mMap = new HashMap<String, String>();
	
	public static void setAttribute(String key, String value){
		mMap.put(key, value);
	}
	
	public static String getAttribute(String key){
		return mMap.get(key);
	}
	private DashCookie() {}
}
