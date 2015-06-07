/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * Copyright (C) 2014 The Android Open Source Project
 *
 * NOTE: This file contains code from:
 *
 *     MediaCodecAudioTrackRenderer.java
 *
 * taken from The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.sonymobile.seeder.internal;

import static com.sonymobile.seeder.internal.Configuration.DO_COMPENSATE_AUDIO_TIMESTAMP_LATENCY;
import static com.sonymobile.seeder.internal.HandlerHelper.sendMessageAndAwaitResponse;
import static com.sonymobile.seeder.internal.Player.MSG_CODEC_NOTIFY;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CryptoException;
import android.media.MediaCodec.CryptoInfo;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.sonymobile.common.AccessUnit;
import com.sonymobile.common.SendObject;
import com.sonymobile.seeder.MediaError;
import com.sonymobile.seeder.TrackInfo.TrackType;
import com.sonymobile.seeder.internal.drm.DrmSession;

public final class AudioThread extends CodecThread implements Clock {

	private static final boolean LOGS_ENABLED = Configuration.DEBUG || true;

	private static final String TAG = "AudioThread";

	private static final String MEDIA_CRYPTO_KEY = "AudioMediaCrypto";

	private static final long MICROS_PER_SECOND = 1000000L;

	private static final long INVALID_TIMESTAMP_LIMIT = 4293000000L;

	private static final int AUDIO_TIMESTAMP_NOT_SUPPORTED = -123;

	private static final int AUDIO_BUFFERSIZE_MULTIPLIER = 4;

	private HandlerThread mEventThread;

	private EventHandler mEventHandler;

	private HandlerThread mRenderingThread;

	private RenderingHandler mRenderingHandler;

	private MediaCodec mCodec;

	private ByteBuffer[] mInputBuffers;

	private ByteBuffer[] mOutputBuffers;

	private AudioTrack mAudioTrack;

	private MediaSource mSource;

	private boolean mEOS = false;

	private boolean mAllDataRendered = false;

	private boolean mSetupCompleted = false;

	private boolean mStarted = false;

	private float mLeftVolume = -1;

	private float mRightVolume = -1;

	private int mSampleRate;

	private Handler mCallbacks;

	private int mInputBuffer = -1;

	private long mAnchorTimeUs = -1;

	public MediaCrypto mMediaCrypto;

	private long mStoredTimeUs = 0;

	public int mAudioSessionId;

	private AudioTimestamp mAudioTimestamp;

	private Method mAudioTrackGetLatencyMethod;

	private DrmSession mDrmSession;

	private long mAudioTrackResumeSystemTimeUs;

	private long mLastReportedCurrentPositionUs;

	private float mPlaybackSpeed = Util.DEFAULT_PLAYBACK_SPEED;

	private int mMessageDelay = Util.DEFAULT_MESSAGE_DELAY;

	private long mLastTimeStampTimeUs;

	private boolean mRenderAudioInPlatform;

	private boolean mDequeueInputErrorFlag = false;

	private Object mRenderingLock = new Object();

	private Method mSetAudioTrackMethod;

	/*
	 * To send AccessUnit to seeder.
	 */
	private static final int MSG_ACCEPT = 1;
	private static final int MSG_WRITE = 2;
	private static final int MSG_CLOSE = 3;
	private static final String TAG2 = "AideoThread.Socket";
	private ServerSocket mServerSocket;
	private Socket mSocket;
	private HandlerThread mSocketThread;
	private SocketHandler mSocketHandler;
	private LinkedList<AccessUnit> mLinkedList = new LinkedList<AccessUnit>();
	private ObjectOutputStream mSocketOutputStream;
	private boolean isBinded = false;
	private Object mBindedLock = new Object();
	private Object mWriteLock = new Object();

	public AudioThread(MediaFormat format, MediaSource source, int audioSessionId,
			Handler callback, DrmSession drmSession) {
		mEventThread = new HandlerThread("Audio", Process.THREAD_PRIORITY_MORE_FAVORABLE);
		mEventThread.start();

		mEventHandler = new EventHandler(mEventThread.getLooper());

		mRenderingThread = new HandlerThread("Audio-Renderer",
				Process.THREAD_PRIORITY_MORE_FAVORABLE);
		mRenderingThread.start();

		mRenderingHandler = new RenderingHandler(mRenderingThread.getLooper());

		mEventHandler.obtainMessage(MSG_SET_SOURCE, source).sendToTarget();
		mEventHandler.obtainMessage(MSG_SETUP, format).sendToTarget();

		mCallbacks = callback;

		mAudioSessionId = audioSessionId;

		// We need this if getHeadPlayPosition is used so always try to look it
		// up. Maybe not the nicest thing to use reflections to call the method
		// but hey what should we do.....
		try {
			mAudioTrackGetLatencyMethod = AudioTrack.class
					.getMethod("getLatency", (Class<?>[])null);
		} catch (NoSuchMethodException e) {
			// There's no guarantee this method exists. Do nothing.
		}

		mDrmSession = drmSession;

		mSocketThread = new HandlerThread(TAG2);
		mSocketThread.start();
		mSocketHandler = new SocketHandler(mSocketThread.getLooper());
		mSocketHandler.sendEmptyMessage(MSG_ACCEPT);
	}

	public void start() {
		mEventHandler.sendEmptyMessage(MSG_START);
	}

	public void pause() {
		sendMessageAndAwaitResponse(mEventHandler.obtainMessage(MSG_PAUSE));
	}

	public void flush() {
		sendMessageAndAwaitResponse(mEventHandler.obtainMessage(MSG_FLUSH));
	}

	protected void setVolume(float leftVolume, float rightVolume) {
		mLeftVolume = leftVolume;
		mRightVolume = rightVolume;
		if (mAudioTrack != null) {
			mAudioTrack.setStereoVolume(leftVolume, rightVolume);
		}
	}

	public void stop() {
		sendMessageAndAwaitResponse(mEventHandler.obtainMessage(MSG_STOP));
		mEventThread.quit();
		mEventHandler = null;
		mEventThread = null;
		mRenderingThread.quit();
		mRenderingHandler = null;
		mRenderingThread = null;
	}

	@Override
	public void setSpeed(float speed) {
		mEventHandler.obtainMessage(MSG_SET_SPEED, speed).sendToTarget();
	}

	@Override
	public long getCurrentTimeUs() {
		if (mAnchorTimeUs == -1) {
			return mStoredTimeUs;
		}

		long currentPositionUs = getCurrentPlayPositionUs();

		if (currentPositionUs > 0) {
			currentPositionUs += mAnchorTimeUs;
		} else {
			currentPositionUs = mAnchorTimeUs;
		}
		Log.i("accessA","current position: "+currentPositionUs/1000);
		return currentPositionUs;
	}

	public boolean isSetupCompleted() {
		return mSetupCompleted;
	}

	private long getCurrentPlayPositionUs() {
		long systemClockUs = System.nanoTime() / 1000;
		long playPositionUs = -1;

		if (mAllDataRendered) {
			long timeDiffUs = systemClockUs - mLastTimeStampTimeUs;
			playPositionUs = mLastReportedCurrentPositionUs + timeDiffUs;
		} else {
			playPositionUs = getAudioTimestampUs();
		}

		if (playPositionUs == AUDIO_TIMESTAMP_NOT_SUPPORTED) {
			playPositionUs = getPlaybackHeadPositionUs();
		}

		mLastReportedCurrentPositionUs = playPositionUs;
		mLastTimeStampTimeUs = systemClockUs;

		return playPositionUs;
	}

	private long getPlaybackHeadPositionUs() {
		// We always have to compensate for audio latency when using
		// getPlaybackHeadPosition
		return framesToDurationUs(getPlaybackHeadPosition()) - getAudioLatency();
	}

	private long getPlaybackHeadPosition() {
		// AudioTrack getPlaybackHeadPosition returns a signed int that shall be
		// interpreted as unsigned. Store it in a long.
		return 0xFFFFFFFFL & mAudioTrack.getPlaybackHeadPosition();
	}

	private long getAudioTimestampUs() {
		long systemClockUs = System.nanoTime() / 1000;
		long currentTimeUs = -1;
		if (mAudioTimestamp == null) {
			mAudioTimestamp = new AudioTimestamp();
		}

		if (mAudioTrack != null && mAudioTrack.getTimestamp(mAudioTimestamp)) {
			if ((mAudioTimestamp.nanoTime / 1000) < mAudioTrackResumeSystemTimeUs - 40) {
				// Time before resume!!
				return AUDIO_TIMESTAMP_NOT_SUPPORTED;
			}

			// After seek / flush we get strange framePosition. Check and
			// return AUDIO_TIMESTAMP_NOT_SUPPORTED.
			if (mAudioTimestamp.framePosition >= INVALID_TIMESTAMP_LIMIT) {
				return AUDIO_TIMESTAMP_NOT_SUPPORTED;
			}

			// How long ago in the past the audio timestamp is (negative if it's
			// in the future)
			long presentationDiffUs = systemClockUs - (mAudioTimestamp.nanoTime / 1000);
			long framesDiff = durationUsToFrames(presentationDiffUs);
			// The position of the frame that's currently being presented.
			long currentFramePosition = mAudioTimestamp.framePosition + framesDiff;
			currentTimeUs = framesToDurationUs(currentFramePosition);
		} else {
			return AUDIO_TIMESTAMP_NOT_SUPPORTED;
		}

		if (DO_COMPENSATE_AUDIO_TIMESTAMP_LATENCY) {
			currentTimeUs -= getAudioLatency();
		}

		return currentTimeUs;
	}

	private long getAudioLatency() {
		long audioTrackLatencyUs = 0;
		if (mAudioTrackGetLatencyMethod != null && mAudioTrack != null) {
			try {
				audioTrackLatencyUs = (Integer)mAudioTrackGetLatencyMethod.invoke(mAudioTrack,
						(Object[])null) * 1000L;
				// Sanity check that the latency is non-negative.
				audioTrackLatencyUs = Math.max(audioTrackLatencyUs, 0);
			} catch (IllegalAccessException e) {
				// The method existed, but doesn't work. Don't try again.
				mAudioTrackGetLatencyMethod = null;
			} catch (IllegalArgumentException e) {
				// The method existed, but doesn't work. Don't try again.
				mAudioTrackGetLatencyMethod = null;
			} catch (InvocationTargetException e) {
				// The method existed, but doesn't work. Don't try again.
				mAudioTrackGetLatencyMethod = null;
			}
		}
		return audioTrackLatencyUs;
	}

	private long framesToDurationUs(long frameCount) {
		return (frameCount * MICROS_PER_SECOND) / mSampleRate;
	}

	private long durationUsToFrames(long durationUs) {
		return (durationUs * mSampleRate) / MICROS_PER_SECOND;
	}

	public int getAudioSessionId() {
		return mAudioSessionId;
	}

	private int getAudioChannelConfiguration(int channelCount) {
		if (channelCount == 1) {
			return AudioFormat.CHANNEL_OUT_MONO;
		} else if (channelCount == 2) {
			return AudioFormat.CHANNEL_OUT_STEREO;
		} else if (channelCount == 6) {
			return AudioFormat.CHANNEL_OUT_5POINT1;
		}

		return AudioFormat.CHANNEL_OUT_DEFAULT;
	}

	private void configCodecWithAudio(MediaFormat format) {
		try {
			Class<?>[] parameterTypes = {
					MediaFormat.class, AudioTrack.class, MediaCrypto.class, Integer.TYPE
			};
			Method configure = MediaCodec.class.getMethod("configureWithAudioTrack",
					parameterTypes);

			mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

			mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
					24000, AudioFormat.CHANNEL_OUT_STEREO,
					AudioFormat.ENCODING_PCM_16BIT,
					25000, AudioTrack.MODE_STREAM, mAudioSessionId);
			if (mAudioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
				mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.UNKNOWN).sendToTarget();
				return;
			}
			if (LOGS_ENABLED) Log.v(TAG, "invoke configureWithAudioTrack");
			configure.invoke(mCodec, format, mAudioTrack, mMediaCrypto, 0);
			mRenderAudioInPlatform = true;
			if (LOGS_ENABLED) Log.i(TAG, "Render audio in platform");
		} catch (NoSuchMethodException e) {
			if (LOGS_ENABLED) Log.e(TAG, "Method does not exists", e);
		} catch (IllegalAccessException e) {
			if (LOGS_ENABLED) Log.e(TAG, "Exception when configuring MediaCodec", e);
		} catch (IllegalArgumentException e) {
			if (LOGS_ENABLED) Log.e(TAG, "Exception when configuring MediaCodec", e);
		} catch (InvocationTargetException e) {
			if (LOGS_ENABLED) Log.e(TAG, "Exception when configuring MediaCodec", e);
		}
	}

	@SuppressWarnings("deprecation")
	private void doDequeueInputBuffer() {
		try {
			while (mStarted && !mEOS) {

				int inputBufferIndex = mInputBuffer;

				if (inputBufferIndex < 0) {
					try {
						inputBufferIndex = mCodec.dequeueInputBuffer(0);
					} catch (RuntimeException e) {
						if (LOGS_ENABLED) Log.e(TAG, "Exception in dequeueInputBuffer", e);
						mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
								MediaError.UNKNOWN).sendToTarget();
						break;
					}
				}

				if (inputBufferIndex < 0) {
					break;
				}

				AccessUnit accessUnit = mSource.dequeueAccessUnit(TrackType.AUDIO);
				Log.i("accessA","status: "+accessUnit.status+"/ size: "+accessUnit.size+"/ timeMs: "+accessUnit.timeUs/1000);
				//Log.i("accessA","durationUs: "+accessUnit.durationUs+"/ isSyncSample: "+accessUnit.isSyncSample+"/ trackIndex: "+accessUnit.trackIndex);
 
				//synchronized (mWriteLock) {
					//if (mSocket != null && !mSocket.isClosed()) {
				if(accessUnit.status == 0 && accessUnit.timeUs != -1){
						mLinkedList.addLast(accessUnit);
						mSocketHandler.sendEmptyMessage(MSG_WRITE);
				}
					//}
				//}

//				if (accessUnit == null) {
//					if (LOGS_ENABLED) Log.w(TAG, "Warning null AccessUnit");
//					break;
//				}

				if (accessUnit.status == AccessUnit.OK) {
					mInputBuffers[inputBufferIndex].position(0);
					mInputBuffers[inputBufferIndex].put(accessUnit.data, 0, accessUnit.size);

					if (mMediaCrypto != null) {
						if (accessUnit.cryptoInfo == null) {
							if (LOGS_ENABLED) Log.e(TAG, "No cryptoInfo");
							mCallbacks.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
									MediaError.MALFORMED).sendToTarget();
							return;
						}
						mCodec.queueSecureInputBuffer(inputBufferIndex, 0,
								accessUnit.cryptoInfo, accessUnit.timeUs,
								MediaCodec.BUFFER_FLAG_SYNC_FRAME);
					} else {
						mCodec.queueInputBuffer(inputBufferIndex, 0, accessUnit.size,
								accessUnit.timeUs, MediaCodec.BUFFER_FLAG_SYNC_FRAME);
					}

					mInputBuffer = -1;
				} else if (accessUnit.status == AccessUnit.NO_DATA_AVAILABLE) {
					if (LOGS_ENABLED) Log.e(TAG, "No audio data available");
					mInputBuffer = inputBufferIndex;
					break;
				} else {
					if (accessUnit.status == AccessUnit.ERROR) {
						if (LOGS_ENABLED) Log.e(TAG, "queue ERROR");
						mDequeueInputErrorFlag = true;
					}
					if (mMediaCrypto != null) {
						CryptoInfo info = new CryptoInfo();
						info.set(1, new int[] {
								1
						}, new int[] {
								0
						}, null, null, MediaCodec.CRYPTO_MODE_UNENCRYPTED);
						mCodec.queueSecureInputBuffer(inputBufferIndex, 0, info, 0,
								MediaCodec.BUFFER_FLAG_END_OF_STREAM);
					} else {
						mCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0,
								MediaCodec.BUFFER_FLAG_END_OF_STREAM);
					}
					return;
				}
			}

			if (mStarted && !mEOS) {
				mEventHandler.sendEmptyMessageAtTime(MSG_DEQUEUE_INPUT_BUFFER,
						SystemClock.uptimeMillis() + mMessageDelay);
			}
		} catch (IllegalStateException e) {
			if (LOGS_ENABLED) Log.e(TAG, "Codec error", e);
			mCallbacks.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR, MediaError.UNKNOWN)
			.sendToTarget();
		} catch (CryptoException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "CryptoException while queueing input buffer", e);
			int error = getMediaDrmErrorCode(e.getErrorCode());
			mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
					error).sendToTarget();
		}
	}

	@SuppressWarnings("deprecation")
	private void doDequeueOutputBuffer() {
		while (mStarted && !mEOS) {
			Frame frame = removeFrameFromPool();
			int outputBufferIndex;
			try {
				outputBufferIndex = mCodec.dequeueOutputBuffer(frame.info, 0);
			} catch (RuntimeException e) {
				if (LOGS_ENABLED)
					Log.e(TAG, "Exception in dequeueOutputBuffer", e);
				mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.UNKNOWN).sendToTarget();
				return;
			}

			if (outputBufferIndex >= 0) {
				if ((frame.info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					mEOS = true;
					mEventHandler.removeMessages(MSG_DEQUEUE_OUTPUT_BUFFER);
					if (mDequeueInputErrorFlag) {
						mCallbacks.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
								MediaError.UNKNOWN).sendToTarget();
					}
				}

				frame.bufferIndex = outputBufferIndex;
				addDecodedFrame(frame);

			} else {
				// Unused frame give, it back to the pool.
				addFrameToPool(frame);

				if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
					mOutputBuffers = mCodec.getOutputBuffers();
					break;
				} else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
					if (mAudioTrack == null) {
						MediaFormat format = mCodec.getOutputFormat();

						mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
						int channelCount = format
								.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
						int channelConfig = getAudioChannelConfiguration(channelCount);

						int bufferSize = AudioTrack.getMinBufferSize(mSampleRate,
								channelConfig, AudioFormat.ENCODING_PCM_16BIT)
								* AUDIO_BUFFERSIZE_MULTIPLIER;

						mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
								mSampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT,
								bufferSize, AudioTrack.MODE_STREAM, mAudioSessionId);

						if (mAudioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
							mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
									MediaError.UNKNOWN).sendToTarget();
							return;
						}

						mAudioTrack.setPlaybackRate((int)(mSampleRate * mPlaybackSpeed));
						mAudioSessionId = mAudioTrack.getAudioSessionId();
						if (mLeftVolume != -1 && mRightVolume != -1) {
							mAudioTrack.setStereoVolume(mLeftVolume, mRightVolume);
						}
						if (mSetAudioTrackMethod != null) {
							try {
								if (LOGS_ENABLED) Log.v(TAG, "invoking setAudioTrack");
								mSetAudioTrackMethod.invoke(mCodec, mAudioTrack);
								mRenderAudioInPlatform = true;
								if (LOGS_ENABLED) Log.i(TAG, "Render audio in platform");
							} catch (IllegalAccessException e) {
								if (LOGS_ENABLED)
									Log.e(TAG, "Exception when setting new AudioTrack", e);
							} catch (IllegalArgumentException e) {
								if (LOGS_ENABLED)
									Log.e(TAG, "Exception when setting new AudioTrack", e);
							} catch (InvocationTargetException e) {
								if (LOGS_ENABLED)
									Log.e(TAG, "Exception when setting new AudioTrack", e);
							}
						}
					}
				} else {
					break;
				}
			}
		}
		if (mStarted && !mEOS) {
			mEventHandler.sendEmptyMessageAtTime(MSG_DEQUEUE_OUTPUT_BUFFER,
					SystemClock.uptimeMillis() + mMessageDelay);
		}
	}

	@SuppressWarnings("deprecation")
	private void doSetup(MediaFormat format) {
		String mime = format.getString(MediaFormat.KEY_MIME);

		try {
			String codecName = MediaCodecHelper.findDecoder(mime, false, null);

			mCodec = MediaCodec.createByCodecName(codecName);
		} catch (IOException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Could not create codec for mime type " + mime, e);
			mCallbacks.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNSUPPORTED).sendToTarget();
			return;
		} catch (IllegalArgumentException e) {
			mCallbacks.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNSUPPORTED).sendToTarget();
			return;
		}

		if (mDrmSession != null) {
			try {
				mMediaCrypto = mDrmSession.getMediaCrypto(MEDIA_CRYPTO_KEY);
			} catch (IllegalStateException e) {
				if (LOGS_ENABLED) Log.e(TAG, "Exception when obtaining MediaCrypto", e);
				mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.DRM_UNKNOWN).sendToTarget();
				return;
			} catch (MediaCryptoException e) {
				if (LOGS_ENABLED) Log.e(TAG, "Exception when creating MediaCrypto", e);
				mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.DRM_UNKNOWN).sendToTarget();
				return;
			}
		} else {
			try {
				mMediaCrypto = Util.createMediaCrypto(format);
			} catch (MediaCryptoException e) {
				if (LOGS_ENABLED) Log.e(TAG, "Exception when creating MediaCrypto", e);
				mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.DRM_UNKNOWN).sendToTarget();
				return;
			}
		}

		try {
			if (mMediaCrypto != null &&
					mMediaCrypto.requiresSecureDecoderComponent(mime)) {
				Class<?>[] parameterTypes = {AudioTrack.class};
				try {
					mSetAudioTrackMethod = MediaCodec.class.getMethod("setAudioTrack",
							parameterTypes);
				} catch (NoSuchMethodException e) {
					configCodecWithAudio(format);
				}
			}
		} catch (IllegalArgumentException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Exception while querying codec", e);
			mCallbacks.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNSUPPORTED).sendToTarget();
		}

		if (!mRenderAudioInPlatform) {
			try {
				mCodec.configure(format, null, mMediaCrypto, 0);
			} catch (IllegalStateException e) {
				if (LOGS_ENABLED)
					Log.e(TAG, "Exception when configuring MediaCodec", e);
				mCallbacks.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.UNKNOWN).sendToTarget();
				return;
			}
		}

		try {
			mCodec.start();
		} catch (CryptoException e) {
			if (LOGS_ENABLED) Log.e(TAG, "Exception when starting", e);
			mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.DRM_UNKNOWN).sendToTarget();
			return;
		} catch (RuntimeException e) {
			if (LOGS_ENABLED) Log.e(TAG, "Exception when starting", e);
			mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNKNOWN).sendToTarget();
			return;
		}

		try {
			mInputBuffers = mCodec.getInputBuffers();

			mOutputBuffers = mCodec.getOutputBuffers();
		} catch (IllegalStateException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Exception when getting buffers", e);
			mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNKNOWN).sendToTarget();
			return;
		}

		mSetupCompleted = true;
	}

	private void doStart() {
		synchronized (mRenderingLock) {
			mStarted = true;
		}

		mAudioTrackResumeSystemTimeUs = System.nanoTime() / 1000;

		mEventHandler.sendEmptyMessage(MSG_DEQUEUE_INPUT_BUFFER);
		mEventHandler.sendEmptyMessage(MSG_DEQUEUE_OUTPUT_BUFFER);
		mRenderingHandler.sendEmptyMessage(MSG_RENDER);
	}

	private void doPause() {
		synchronized (mRenderingLock) {
			mStarted = false;
			if (mAudioTrack != null) {
				mAudioTrack.pause();
			}
		}
	}

	private void doFlush() {
		synchronized (mRenderingLock) {
			mStoredTimeUs = getCurrentTimeUs();
			clearDecodedFrames();
			if (mAudioTrack != null) {
				mAudioTrack.pause();
				mAudioTrack.flush();
			}
			try {
				mCodec.flush();
			} catch (RuntimeException e) {
				if (LOGS_ENABLED) Log.e(TAG, "Exception in flush", e);
				mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.UNKNOWN).sendToTarget();
			}
			mInputBuffer = -1;
			mAnchorTimeUs = -1;
			mEOS = false;
			mAllDataRendered = false;
			mLastReportedCurrentPositionUs = 0;
			mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_FLUSH_COMPLETED, 0)
			.sendToTarget();
		}
	}

	private void doStop() {
		synchronized (mRenderingLock) {
			mStarted = false;
			if (mAudioTrack != null) {
				mAudioTrack.release();
				mAudioTrack = null;
			}
			mCodec.release();
			if (mMediaCrypto != null && mDrmSession == null) {
				// Only release the MediaCrypto object if not handled by a DRMSession.
				mMediaCrypto.release();
			}

			mEventHandler.removeCallbacksAndMessages(null);
			mRenderingHandler.removeCallbacksAndMessages(null);
		}
	}

	private void doSetSpeed(float speed) {
		synchronized (mRenderingLock) {
			mPlaybackSpeed = speed;
			mMessageDelay = (mPlaybackSpeed > Util.DEFAULT_PLAYBACK_SPEED) ?
					(int)(Util.DEFAULT_MESSAGE_DELAY / mPlaybackSpeed)
					: Util.DEFAULT_MESSAGE_DELAY;

					if (mAudioTrack != null) {
						mAudioTrack.setPlaybackRate((int)(mSampleRate * mPlaybackSpeed));
					}
		}
	}

	private void doRender() {
		synchronized (mRenderingLock) {
			while (mStarted) {
				Frame frame = peekDecodedFrame();

				if (frame != null) {

					if ((frame.info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
						mAllDataRendered = true;
						mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY,
								CODEC_NOTIFY_POSITION,
								(int)(getCurrentTimeUs() / 1000))
								.sendToTarget();

						mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY,
								CODEC_AUDIO_COMPLETED, 0).sendToTarget();
						removeFirstDecodedFrame();
						return;
					}

					BufferInfo info = frame.info;
					int outputBufferIndex = frame.bufferIndex;

					if (!mRenderAudioInPlatform) {
						if (frame.byteBuffer == null || frame.byteBuffer.length < info.size) {
							// We need a bigger buffer!
							frame.byteBuffer = new byte[info.size];
						}

						mOutputBuffers[outputBufferIndex].get(frame.byteBuffer, 0, info.size);

						mAudioTrack.write(frame.byteBuffer, 0, info.size);
						mOutputBuffers[outputBufferIndex].clear();
					}

					mCallbacks.obtainMessage(Player.MSG_CODEC_NOTIFY,
							CODEC_NOTIFY_POSITION,
							(int)(getCurrentTimeUs() / 1000))
							.sendToTarget();
					if (mAnchorTimeUs == -1) {
						mAnchorTimeUs = info.presentationTimeUs;
						mStoredTimeUs = -1;
					}

					try {
						mCodec.releaseOutputBuffer(outputBufferIndex, mRenderAudioInPlatform);
					} catch (IllegalStateException e) {
						if (LOGS_ENABLED)
							Log.e(TAG, "Codec error while releasing outputbuffer", e);
						mCallbacks.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR, MediaError.UNKNOWN)
						.sendToTarget();
						return;
					}

					int playState = mAudioTrack.getPlayState();
					if (playState != AudioTrack.PLAYSTATE_PLAYING) {
						mAudioTrack.play();
					}

					frame = removeFirstDecodedFrame();
					addFrameToPool(frame);
				} else {
					// No frames available, let us wait....
					try {
						mRenderingLock.wait(mMessageDelay);
					} catch (InterruptedException e) {
						// ignored.....
					}
				}
			}
		}
	}

	public void setSeekTimeUs(long timeUs) {
		if (LOGS_ENABLED) Log.d(TAG, "setSeekTime = " + timeUs);
		mStoredTimeUs = timeUs;
		mLastReportedCurrentPositionUs = 0;
	}

	private void onAccept() {
		try {
			synchronized (mBindedLock) {
				if (!isBinded) {
					if (mServerSocket == null || !mServerSocket.isBound()) {
						Log.i(TAG, "before make server socket");
						mServerSocket = new ServerSocket(55100, 1);
						Log.i(TAG, "after make server socket");
						isBinded = true;
					}
				} else {
					Log.i(TAG, "server socket already binded");
					return;
				}
			}
			if(mSocket != null && mSocket.isConnected()) {
				Log.w(TAG, "one client already connected");
				return;
			}
			try {
				//synchronized (mWriteLock) {
					mSocket = mServerSocket.accept();
					mSocketOutputStream = new ObjectOutputStream(mSocket.getOutputStream());
				//}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
				mSocketHandler.sendEmptyMessage(MSG_CLOSE);
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void onClose() {
		try {
			if (mSocket != null && mSocket.isConnected()) {
				mSocketOutputStream.close();
				mSocketOutputStream = null;
				mSocket.close();
				mSocket = null;
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void onWrite() {
		try {
			if (mSocket == null || mSocket.isClosed()) {
				//Log.i(TAG, "socket is closed");
				return;
			}
			SendObject sobj = new SendObject(mLinkedList.removeFirst());
			mSocketOutputStream.writeObject(sobj);
			mSocketOutputStream.flush();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
			mSocketHandler.sendEmptyMessage(MSG_CLOSE);
		}
	}

	private class SocketHandler extends Handler {

		public SocketHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_ACCEPT:
				Log.i(TAG, "before onAccept");
				onAccept();
				Log.i(TAG, "after onAccept");
				break;
			case MSG_WRITE:
				onWrite();
				break;
			case MSG_CLOSE:
				onClose();
				break;
			}
		}
	}

	class EventHandler extends Handler {

		public EventHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_SET_SOURCE:
				mSource = (MediaSource)msg.obj;
				break;
			case MSG_SETUP:
				doSetup((MediaFormat)msg.obj);
				break;
			case MSG_START:
				doStart();
				break;
			case MSG_PAUSE: {
				doPause();

				Handler replyHandler = (Handler)msg.obj;
				Message reply = replyHandler.obtainMessage();
				reply.obj = new Object();
				reply.sendToTarget();
				break;
			}
			case MSG_DEQUEUE_INPUT_BUFFER:
				doDequeueInputBuffer();
				break;
			case MSG_DEQUEUE_OUTPUT_BUFFER:
				doDequeueOutputBuffer();
				break;
			case MSG_FLUSH: {
				doFlush();

				Handler replyHandler = (Handler)msg.obj;
				Message reply = replyHandler.obtainMessage();
				reply.obj = new Object();
				reply.sendToTarget();

				break;
			}
			case MSG_STOP: {
				doStop();

				Handler replyHandler = (Handler)msg.obj;
				Message reply = replyHandler.obtainMessage();
				reply.obj = new Object();
				reply.sendToTarget();
				break;
			}
			case MSG_SET_SPEED: {
				doSetSpeed((Float)msg.obj);
				break;
			}
			default:
				if (LOGS_ENABLED) Log.w(TAG, "Unknown message");
				break;
			}
		}
	}

	private class RenderingHandler extends Handler {

		public RenderingHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_RENDER:
				doRender();
				break;
			default:
				if (LOGS_ENABLED) Log.w(TAG, "Unknown message");
				break;

			}
		}
	}
}
