package com.example.dashclient;

public interface SocketHandler {

	public String getName();
	
	public void onSuccess();
	
	public void onSuccess(Object... args);
	
	public void onFailure(String message);
}
