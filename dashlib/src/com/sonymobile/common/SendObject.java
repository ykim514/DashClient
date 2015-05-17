package com.sonymobile.common;

import java.io.Serializable;

public class SendObject implements Serializable{
	//AccessUnit info
	public int mStatus;
	public int mSize;
	public byte[] mData;
	public long mTimeUs;
	public long mDurationUs;
	public boolean mIsSyncSample;
	public int mTrackIndex;
	
	//MediaFormat info
	public int mMediaFormat;
	
	//Crypto info
	public byte[] iv;
	public byte[] key;
	public int mode;
	public int[] numBytesOfClearData;
	public int[] numBytesOfEncryptedData;
	public int numSubSamples;
	
	public SendObject(AccessUnit au){
		mStatus = au.status;
		mSize = au.size;
		mData = au.data;
		mTimeUs = au.timeUs;
		mDurationUs = au.durationUs;
		mIsSyncSample = au.isSyncSample;
		mTrackIndex = au.trackIndex;

		if(au.cryptoInfo != null){
			iv = au.cryptoInfo.iv;
			key = au.cryptoInfo.key;
			mode = au.cryptoInfo.mode;
			numBytesOfClearData = au.cryptoInfo.numBytesOfClearData;
			numBytesOfEncryptedData = au.cryptoInfo.numBytesOfEncryptedData;
			numSubSamples = au.cryptoInfo.numSubSamples;
		}
	}
	
	public AccessUnit makeAccessUnit(){
		AccessUnit au = new AccessUnit(mStatus);
		au.size = mSize;
		au.data = mData;
		au.timeUs = mTimeUs;
		au.durationUs = mDurationUs;
		au.isSyncSample = mIsSyncSample;
		au.trackIndex = mTrackIndex;
		if(au.cryptoInfo != null)
			au.cryptoInfo.set(numSubSamples, numBytesOfClearData, numBytesOfEncryptedData, key, iv, mode);
		
		return au;
	}
}

