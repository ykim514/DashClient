package com.example.dashclient;

public class DashMedia {
	private int id;
	private String name;
	private int size;
	private String path;

	public DashMedia() {
	}
	public DashMedia(int id, String name, int size, String path){
		this.id = id;
		this.name = name;
		this.size = size;
		this.path = path;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getLink() {
		return DashHttpClient.HOST + "/" + path;
	}
}