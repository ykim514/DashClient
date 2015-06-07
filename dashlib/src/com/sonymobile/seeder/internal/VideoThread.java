/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
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

import static com.sonymobile.seeder.internal.HandlerHelper.sendMessageAndAwaitResponse;
import static com.sonymobile.seeder.internal.Player.MSG_CODEC_NOTIFY;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodec.CryptoException;
import android.media.MediaCodec.CryptoInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import com.sonymobile.common.AccessUnit;
import com.sonymobile.common.SendObject;
import com.sonymobile.seeder.MediaError;
import com.sonymobile.seeder.MetaData;
import com.sonymobile.seeder.TrackInfo.TrackType;
import com.sonymobile.seeder.internal.drm.DrmSession;

public final class VideoThread extends VideoCodecThread {

	private static final boolean LOGS_ENABLED = Configuration.DEBUG || true;

	private static final String TAG = "VideoThread";

	private static final String MEDIA_CRYPTO_KEY = "VideoMediaCrypto";

	private static final int LATE_FRAME_TIME_MS = -40;

	private static final int MAX_EARLY_FRAME_TIME_ALLOWED_MS = 10;

	private HandlerThread mEventThread;

	private EventHandler mEventHandler;

	private HandlerThread mRenderingThread;

	private RenderingHandler mRenderingHandler;

	private MediaCodec mCodec;

	private ByteBuffer[] mInputBuffers;

	private MediaSource mSource;

	private Surface mSurface;

	private boolean mEOS = false;

	private Clock mClock;

	private boolean mStarted = false;

	private boolean mSeeking = false;

	private Handler mCallback;

	private int mInputBuffer = -1;

	private MediaCrypto mMediaCrypto;

	private boolean mSetupCompleted = false;

	private boolean mReadyToRender = false;

	private boolean mUpdateAudioClock = false;

	private MediaFormat mFormat;

	private boolean mSupportsAdaptivePlayback;

	private boolean mSkipToIframe;

	private DrmSession mDrmSession;

	private int mVideoScalingMode;

	private float mCurrentSpeed = 1.0f;

	private int mSampleAspectRatioWidth = 1;

	private int mSampleAspectRatioHeight = 1;

	private int mWidth = 0;

	private int mHeight = 0;

	private int mNumRenderFrames = 0;

	private int mNumDroppedFrames = 0;

	private boolean mVideoRenderingStarted = false;

	private boolean mDequeueInputErrorFlag = false;

	private Object mRenderingLock = new Object();

	private HashMap<String, Integer> mCustomMediaFormatParams;

	private boolean mHasQueuedInputBuffers = false;

	private int mDelayCounter = 0;

	private boolean mCheckAudioClockAfterResume = false;

	private long mResumeTimeUs = 0;

	private long mLastAudioTimeUs = 0;

	/*
	 * To send AccessUnit to seeder.
	 */
	private static final int MSG_ACCEPT = 1;
	private static final int MSG_WRITE = 2;
	private static final int MSG_CLOSE = 3;
	private static final String TAG2 = "VideoThread.Socket";
	private ServerSocket mServerSocket;
	private Socket mSocket;
	private HandlerThread mSocketThread;
	private SocketHandler mSocketHandler;
	private LinkedList<AccessUnit> mLinkedList = new LinkedList<AccessUnit>();
	private ObjectOutputStream mSocketOutputStream;
	private boolean isBinded = false;
	private Object mBindedLock = new Object();

	public VideoThread(MediaFormat format, MediaSource source, Surface surface,
			Clock clock, Handler callback, DrmSession drmSession,
			int videoScalingMode,
			HashMap<String, Integer> customMediaFormatParams) {
		super();
		mEventThread = new HandlerThread("Video",
				Process.THREAD_PRIORITY_MORE_FAVORABLE);
		mEventThread.start();

		mEventHandler = new EventHandler(mEventThread.getLooper());

		mRenderingThread = new HandlerThread("Video-Render",
				Process.THREAD_PRIORITY_MORE_FAVORABLE);
		mRenderingThread.start();

		mRenderingHandler = new RenderingHandler(mRenderingThread.getLooper());

		mEventHandler.obtainMessage(MSG_SET_SOURCE, source).sendToTarget();
		mEventHandler.obtainMessage(MSG_SET_SURFACE, surface).sendToTarget();
		mEventHandler.obtainMessage(MSG_SETUP, format).sendToTarget();

		mClock = clock;
		mCallback = callback;
		mUpdateAudioClock = false;

		mDrmSession = drmSession;
		mVideoScalingMode = videoScalingMode;

		mCustomMediaFormatParams = customMediaFormatParams;
		mHasQueuedInputBuffers = false;

		// Socket
		mSocketThread = new HandlerThread(TAG2);
		mSocketThread.start();
		mSocketHandler = new SocketHandler(mSocketThread.getLooper());
		mSocketHandler.sendEmptyMessage(MSG_ACCEPT);
	}

	@Override
	public void setWidth(int width) {
		mWidth = width;
	}

	@Override
	public void setHeight(int height) {
		mHeight = height;
	}

	@Override
	public void seek() {
		// we were likely paused to perform the seek but as we are seeking,
		// mLastAudioTimeUs is not correct
		mCheckAudioClockAfterResume = false;
		if (!mSeeking) {
			mSeeking = true;
			mRenderingHandler.removeMessages(MSG_RENDER);
			// Kick-start the decoding....
			mEventHandler.sendEmptyMessage(MSG_DEQUEUE_INPUT_BUFFER);
			mEventHandler.sendEmptyMessage(MSG_DEQUEUE_OUTPUT_BUFFER);
			mRenderingHandler.sendEmptyMessage(MSG_RENDER_ONE_FRAME);
		}
	}

	@Override
	public void start() {
		mEventHandler.sendEmptyMessage(MSG_START);
	}

	@Override
	public void pause() {
		sendMessageAndAwaitResponse(mEventHandler.obtainMessage(MSG_PAUSE));
	}

	@Override
	public void flush() {
		sendMessageAndAwaitResponse(mEventHandler.obtainMessage(MSG_FLUSH));
	}

	@Override
	public void stop() {
		sendMessageAndAwaitResponse(mEventHandler.obtainMessage(MSG_STOP));
		mEventThread.quit();
		mRenderingThread.quit();
		mEventThread = null;
		mEventHandler = null;
		mRenderingHandler = null;
		mRenderingThread = null;
	}

	@Override
	public boolean isSetupCompleted() {
		return mSetupCompleted;
	}

	@Override
	public boolean isReadyToRender() {
		return mReadyToRender;
	}

	@Override
	public void updateAudioClockOnNextVideoFrame() {
		mUpdateAudioClock = true;
	}

	@Override
	public void setVideoScalingMode(int mode) {
		mEventHandler.obtainMessage(MSG_SET_VIDEO_SCALING_MODE, mode, 0)
		.sendToTarget();
	}

	@Override
	public void setSpeed(float speed) {
		mCurrentSpeed = speed;
	}

	private void updateAspectRatio(MediaFormat mediaFormat) {
		mSampleAspectRatioWidth = 1;
		mSampleAspectRatioHeight = 1;

		if (mediaFormat.containsKey(MetaData.KEY_SAR_WIDTH)
				&& mediaFormat.containsKey(MetaData.KEY_SAR_HEIGHT)) {
			mSampleAspectRatioWidth = mediaFormat
					.getInteger(MetaData.KEY_SAR_WIDTH);
			mSampleAspectRatioHeight = mediaFormat
					.getInteger(MetaData.KEY_SAR_HEIGHT);
		} else if (mSource.getMetaData().containsKey(
				MetaData.KEY_HMMP_PIXEL_ASPECT_RATIO)) {
			int pixelAspectRatio = mSource.getMetaData().getInteger(
					MetaData.KEY_HMMP_PIXEL_ASPECT_RATIO);
			mSampleAspectRatioWidth = (pixelAspectRatio >> 16) & 0xFFFF;
			mSampleAspectRatioHeight = pixelAspectRatio & 0xFFFF;
		}

		if (mSampleAspectRatioWidth == 0 || mSampleAspectRatioHeight == 0) {
			mSampleAspectRatioWidth = 1;
			mSampleAspectRatioHeight = 1;
		}
	}

	private void addCustomMediaFormatParams(MediaFormat format) {
		if (mCustomMediaFormatParams != null) {
			Set<String> keys = mCustomMediaFormatParams.keySet();
			for (String key : keys) {
				int value = mCustomMediaFormatParams.get(key);
				if (LOGS_ENABLED)
					Log.d(TAG, "Adding Custom Param " + key + " : " + value);
				format.setInteger(key, value);
			}
		}
	}

	private void doSetup(MediaFormat format) {
		mFormat = format;
		mHasQueuedInputBuffers = false;

		String mime = format.getString(MediaFormat.KEY_MIME);

		addCustomMediaFormatParams(format);
		updateAspectRatio(mFormat);

		if (mDrmSession != null) {
			try {
				mMediaCrypto = mDrmSession.getMediaCrypto(MEDIA_CRYPTO_KEY);
			} catch (IllegalStateException e) {
				if (LOGS_ENABLED)
					Log.e(TAG, "Exception when obtaining MediaCrypto", e);
				mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.DRM_UNKNOWN).sendToTarget();
				return;
			} catch (MediaCryptoException e) {
				if (LOGS_ENABLED)
					Log.e(TAG, "Exception when creating MediaCrypto", e);
				mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.DRM_UNKNOWN).sendToTarget();
				return;
			}
		} else {
			try {
				mMediaCrypto = Util.createMediaCrypto(format);
			} catch (MediaCryptoException e) {
				if (LOGS_ENABLED)
					Log.e(TAG, "Exception when creating MediaCrypto", e);
				mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.DRM_UNKNOWN).sendToTarget();
				return;
			}
		}

		MediaCodecInfo[] codecInfo = new MediaCodecInfo[1];
		try {
			String codecName = MediaCodecHelper.findDecoder(
					mime,
					mMediaCrypto != null
					&& mMediaCrypto
					.requiresSecureDecoderComponent(mime),
					codecInfo);

			mCodec = MediaCodec.createByCodecName(codecName);
		} catch (IOException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Could not create codec for mime type " + mime, e);
			mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNSUPPORTED).sendToTarget();
			return;
		} catch (IllegalArgumentException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Exception while querying codec", e);
			mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNSUPPORTED).sendToTarget();
			return;
		}

		try {
			mCodec.configure(format, mSurface, mMediaCrypto, 0);
		} catch (IllegalStateException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Exception when configuring MediaCodec", e);
			mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNKNOWN).sendToTarget();
			return;
		} catch (IllegalArgumentException e) {
			if (LOGS_ENABLED)
				Log.e(TAG,
						"IllegalArgumentException when configuring MediaCodec",
						e);
			mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNKNOWN).sendToTarget();
			return;
		}

		if (mVideoScalingMode > 0) {
			mCodec.setVideoScalingMode(mVideoScalingMode);
		}

		mSupportsAdaptivePlayback = codecInfo[0].getCapabilitiesForType(mime)
				.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback);

		try {
			mCodec.start();
		} catch (CryptoException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Exception when starting", e);
			mCallback.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.DRM_UNKNOWN).sendToTarget();
			return;
		} catch (RuntimeException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Exception when starting", e);
			mCallback.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNKNOWN).sendToTarget();
			return;
		}

		try {
			mInputBuffers = mCodec.getInputBuffers();
		} catch (IllegalStateException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Exception when getting buffers", e);
			mCallback.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNKNOWN).sendToTarget();
			return;
		}

		mSetupCompleted = true;
		mSkipToIframe = true;
	}

	private void doStart() {
		synchronized (mRenderingLock) {
			mStarted = true;
		}

		mResumeTimeUs = System.nanoTime() / 1000;

		if (!mSeeking) {
			mRenderingHandler.sendEmptyMessage(MSG_RENDER);
			mEventHandler.sendEmptyMessage(MSG_DEQUEUE_INPUT_BUFFER);
			mEventHandler.sendEmptyMessage(MSG_DEQUEUE_OUTPUT_BUFFER);
		}
	}

	private void doPause() {
		synchronized (mRenderingLock) {
			mStarted = false;
		}

		mCheckAudioClockAfterResume = true;

		mRenderingHandler.removeMessages(MSG_RENDER);
		if (!mSeeking) {
			mEventHandler.removeMessages(MSG_DEQUEUE_INPUT_BUFFER);
			mEventHandler.removeMessages(MSG_DEQUEUE_OUTPUT_BUFFER);
			mRenderingHandler.removeMessages(MSG_RENDER_ONE_FRAME);
		}
	}

	private void doStop() {
		synchronized (mRenderingLock) {
			mStarted = false;
			if (mCodec != null) {
				mCodec.release();
			}
			if (mMediaCrypto != null && mDrmSession == null) {
				// Only release the MediaCrypto object if not handled by a
				// DRMSession.
				mMediaCrypto.release();
			}

			mEventHandler.removeCallbacksAndMessages(null);
			mRenderingHandler.removeCallbacksAndMessages(null);
			
			//socket close
			mSocketHandler.obtainMessage(MSG_CLOSE).sendToTarget();
		}
	}

	private void doFlush() {
		if (!mHasQueuedInputBuffers && !mEOS) {
			// No need to flush if no input buffers are queued
			return;
		}
		synchronized (mRenderingLock) {
			try {
				mReadyToRender = false;
				mHasQueuedInputBuffers = false;
				mCodec.flush();
			} catch (RuntimeException e) {
				if (LOGS_ENABLED)
					Log.e(TAG, "Exception in flush", e);
				mCallback.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.UNKNOWN).sendToTarget();
			}
			mInputBuffer = -1;
			clearDecodedFrames();
			mEOS = false;
		}
	}

	private void doDequeueInputBuffer() {
		try {
			Log.i(TAG,"doDequeueInputBuffer()");
			while ((mStarted || mSeeking) && !mEOS) {
				Log.i(TAG,"in while");
				int inputBufferIndex = mInputBuffer;

				if (inputBufferIndex < 0) {
					try {
						inputBufferIndex = mCodec.dequeueInputBuffer(1000);
					} catch (RuntimeException e) {
						if (LOGS_ENABLED)
							Log.e(TAG, "Exception in dequeueInputBuffer", e);
						mCallback.obtainMessage(Player.MSG_CODEC_NOTIFY,
								CODEC_ERROR, MediaError.UNKNOWN).sendToTarget();
						break;
					}
				}

				if (inputBufferIndex < 0) {
					break;
				}
				
				AccessUnit accessUnit = mSource.dequeueAccessUnit(TrackType.VIDEO);
				//Log.i("accessV","status: "+accessUnit.status+"/ size: "+accessUnit.size+"/ timeMs: "+accessUnit.timeUs/1000);
				//Log.i("accessV","durationUs: "+accessUnit.durationUs+"/ isSyncSample: "+accessUnit.isSyncSample+"/ trackIndex: "+accessUnit.trackIndex);
				//if(!mSocket.isConnected())
				//	mSocketHandler.sendEmptyMessage(MSG_ACCEPT);
				if((accessUnit.status == 0 && accessUnit.timeUs != -1) || accessUnit.status == -3){
					mLinkedList.addLast(accessUnit);
					mSocketHandler.sendEmptyMessage(MSG_WRITE);
					//Log.i(TAG, "status: "+accessUnit.status);
				}
				// Send to Peer
				// Message msg = mSocketHandler.obtainMessage(MSG_WRITE,accessUnit);
				//Log.i(TAG,"send write message");
				// mSocketHandler.sendMessage(msg);
				//onWrite(accessUnit);
				//

				if (accessUnit.status == AccessUnit.OK) {
					if (mSkipToIframe && !accessUnit.isSyncSample) {
						mInputBuffer = inputBufferIndex;
						if (LOGS_ENABLED)
							Log.i(TAG, "Drop non iframe");
					} else {
						mSkipToIframe = false;

						mInputBuffers[inputBufferIndex].position(0);
						mInputBuffers[inputBufferIndex].put(accessUnit.data, 0,
								accessUnit.size);
						
						Log.i("accessV","status: "+accessUnit.status+"/ size: "+accessUnit.size+"/ timeMs: "+accessUnit.timeUs/1000);
						Log.i("accessV","durationUs: "+accessUnit.durationUs+"/ isSyncSample: "+accessUnit.isSyncSample+"/ trackIndex: "+accessUnit.trackIndex);

						if (mMediaCrypto != null) {
							if (accessUnit.cryptoInfo == null) {
								if (LOGS_ENABLED)
									Log.e(TAG, "No cryptoInfo");
								mReadyToRender = true;
								mCallback.obtainMessage(MSG_CODEC_NOTIFY,
										CODEC_ERROR, MediaError.MALFORMED)
										.sendToTarget();
								return;
							}
							mCodec.queueSecureInputBuffer(
									inputBufferIndex,
									0,
									accessUnit.cryptoInfo,
									accessUnit.timeUs,
									accessUnit.isSyncSample ? MediaCodec.BUFFER_FLAG_SYNC_FRAME
											: 0);
						} else {
							mCodec.queueInputBuffer(
									inputBufferIndex,
									0,
									accessUnit.size,
									accessUnit.timeUs,
									accessUnit.isSyncSample ? MediaCodec.BUFFER_FLAG_SYNC_FRAME
											: 0);
						}

						mHasQueuedInputBuffers = true;
						mInputBuffer = -1;
					}
				} else if (accessUnit.status == AccessUnit.FORMAT_CHANGED) {
					Log.i("TAG","format: "+accessUnit.format);
					Log.i("TAG","format: "+mFormat);
					if (accessUnit.format != mFormat) {
						if (mSupportsAdaptivePlayback) {
							mFormat = accessUnit.format;
							updateAspectRatio(mFormat);
						} else {
							if (mCodec != null) {
								mCodec.release();
							}
							if (mMediaCrypto != null) {
								mMediaCrypto.release();
							}

							mEventHandler
							.removeMessages(MSG_DEQUEUE_OUTPUT_BUFFER);
							mEventHandler
							.removeMessages(MSG_DEQUEUE_INPUT_BUFFER);

							clearDecodedFrames();
							mInputBuffer = -1;

							mEventHandler.obtainMessage(MSG_SETUP,
									accessUnit.format).sendToTarget();
							mEventHandler.sendEmptyMessage(MSG_START);

							break;
						}
					}

					mInputBuffer = inputBufferIndex;
				} else if (accessUnit.status == AccessUnit.NO_DATA_AVAILABLE) {
					if (LOGS_ENABLED)
						Log.e(TAG, "no data available");
					mInputBuffer = inputBufferIndex;
					break;
				} else {
					if (accessUnit.status == AccessUnit.ERROR) {
						if (LOGS_ENABLED)
							Log.e(TAG, "queue ERROR");
						mDequeueInputErrorFlag = true;
					}
					if (LOGS_ENABLED)
						Log.e(TAG, "queue EOS");
					if (mMediaCrypto != null) {
						CryptoInfo info = new CryptoInfo();
						info.set(1, new int[] { 0 }, new int[] { 0 }, null,
								null, MediaCodec.CRYPTO_MODE_UNENCRYPTED);
						mCodec.queueSecureInputBuffer(inputBufferIndex, 0,
								info, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
					} else {
						mCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0,
								MediaCodec.BUFFER_FLAG_END_OF_STREAM);
					}
					return;
				}
			}
			long delay = mCurrentSpeed > 1 ? (long) (10f / mCurrentSpeed) : 10;
			if (mSeeking) {
				delay = 0;
			}
			mEventHandler.sendEmptyMessageAtTime(MSG_DEQUEUE_INPUT_BUFFER,
					SystemClock.uptimeMillis() + delay);

		} catch (IllegalStateException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Codec error", e);
			mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNKNOWN).sendToTarget();
		} catch (MediaCodec.CryptoException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Crypto Error", e);
			int error = getMediaDrmErrorCode(e.getErrorCode());
			mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR, error)
			.sendToTarget();
		}
	}

	private void doDequeueOutputBuffer() {
		try {
			while ((mStarted || mSeeking) && !mEOS) {
				Log.i(TAG,"frame size: "+(framePoolCount()+decodedFrameCount()));
				Frame frame = removeFrameFromPool();
				int outputBufferIndex;
				try {
					outputBufferIndex = mCodec.dequeueOutputBuffer(frame.info, 0);
				} catch (RuntimeException e) {
					if (LOGS_ENABLED)
						Log.e(TAG, "Exception in dequeueOutputBuffer", e);
					mCallback.obtainMessage(Player.MSG_CODEC_NOTIFY,
							CODEC_ERROR, MediaError.UNKNOWN).sendToTarget();
					break;
				}

				if (outputBufferIndex >= 0) {
					if (mUpdateAudioClock) {	//initial
						mClock.setSeekTimeUs(frame.info.presentationTimeUs);
						mUpdateAudioClock = false;
					}

					// EOS, stop dequeue
					if ((frame.info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
						addDecodedFrame(frame);
						mEOS = true;
						mReadyToRender = true;
						if (mDequeueInputErrorFlag) {
							mCallback.obtainMessage(MSG_CODEC_NOTIFY,
									CODEC_ERROR, MediaError.UNKNOWN)
									.sendToTarget();
						}
						return;
					}

					mReadyToRender = true;
					frame.bufferIndex = outputBufferIndex;

					long delayMs = (frame.info.presentationTimeUs - mClock
							.getCurrentTimeUs()) / 1000;

					if (delayMs > LATE_FRAME_TIME_MS || mSeeking) {
						addDecodedFrame(frame);
						Log.i(TAG,"frame: "+frame.info.presentationTimeUs/1000);
					} else {
						// Already late... throw it away and dequeue
						// again!
						if (LOGS_ENABLED)
							Log.w(TAG, "Frame dropped in Dequeue! ("
									+ (++mNumDroppedFrames) + ") #("
									+ mNumRenderFrames + ") is too late with: "
									+ delayMs);

						mCodec.releaseOutputBuffer(frame.bufferIndex, false);
						mNumRenderFrames++;
						addFrameToPool(frame);
					}
				} else {
					// Unused frame, give it back to the pool.
					addFrameToPool(frame);

					if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

						MediaFormat newFormat = mCodec.getOutputFormat();

						int newWidth = (newFormat
								.getInteger(MediaFormat.KEY_WIDTH) * mSampleAspectRatioWidth)
								/ mSampleAspectRatioHeight;
						int newHeight = newFormat
								.getInteger(MediaFormat.KEY_HEIGHT);

						if (newWidth != mWidth || newHeight != mHeight) {
							mWidth = newWidth;
							mHeight = newHeight;

							Message msg = mCallback.obtainMessage(
									MSG_CODEC_NOTIFY,
									CODEC_VIDEO_FORMAT_CHANGED, 0);
							Bundle data = new Bundle();
							data.putInt(MetaData.KEY_WIDTH, mWidth);
							data.putInt(MetaData.KEY_HEIGHT, mHeight);
							msg.setData(data);

							msg.sendToTarget();
						}
					}
					break;
				}
			}
		} catch (IllegalStateException e) {
			if (LOGS_ENABLED)
				Log.e(TAG, "Codec error", e);
			mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
					MediaError.UNKNOWN).sendToTarget();
			return;
		}

		long delayMs = mCurrentSpeed > 1 ? (long) (10f / mCurrentSpeed) : 10;
		if (mSeeking) {
			delayMs = 0;
		}

		mEventHandler.sendEmptyMessageAtTime(MSG_DEQUEUE_OUTPUT_BUFFER,
				SystemClock.uptimeMillis() + delayMs);
	}

	private void doRender() {
		synchronized (mRenderingLock) {
			if (mStarted) {
				Frame frame = peekDecodedFrame();

				if (frame == null) {
					long delayMs = (long) (10.0f / mCurrentSpeed);
					// No frames repost
					mRenderingHandler.sendEmptyMessageAtTime(MSG_RENDER,
							SystemClock.uptimeMillis() + delayMs);
					return;
				}

				if ((frame.info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					mRenderingHandler.removeMessages(MSG_RENDER);
					mCallback.obtainMessage(MSG_CODEC_NOTIFY,
							CODEC_VIDEO_COMPLETED, 0).sendToTarget();
					removeFirstDecodedFrame();
					return;
				}

				long currentClockTimeUs = mClock.getCurrentTimeUs();

				long delayMs = (frame.info.presentationTimeUs - currentClockTimeUs) / 1000;
				//Log.i(TAG,"frame ms: "+ frame.info.presentationTimeUs/1000+" , current ms: "+currentClockTimeUs/1000);

				if (delayMs > MAX_EARLY_FRAME_TIME_ALLOWED_MS) {
					// if (LOGS_ENABLED) Log.w(TAG, "Frame early! (" +
					// (++mNumEarlyFrames)
					// + ") is early with " + delayMs);

					if (mCheckAudioClockAfterResume) {
						long systemTimeUs = System.nanoTime() / 1000;
						delayMs = (frame.info.presentationTimeUs - (mLastAudioTimeUs + (systemTimeUs - mResumeTimeUs))) / 1000;

					}
					if (delayMs > MAX_EARLY_FRAME_TIME_ALLOWED_MS) {
						if (mDelayCounter++ < 20 && delayMs > 100) {
							delayMs = MAX_EARLY_FRAME_TIME_ALLOWED_MS;
						} else {
							mDelayCounter = 0;
						}

						delayMs = (long) ((float) delayMs / mCurrentSpeed);

						mRenderingHandler.sendEmptyMessageAtTime(MSG_RENDER,
								SystemClock.uptimeMillis() + delayMs);
						return;
					}
				} else {
					mCheckAudioClockAfterResume = false;
					mResumeTimeUs = 0;
				}

				try {
					if (delayMs < LATE_FRAME_TIME_MS) {
						if (LOGS_ENABLED)
							Log.w(TAG, "Frame dropped! ("
									+ (++mNumDroppedFrames) + ") #("
									+ mNumRenderFrames + ") is too late with: "
									+ delayMs);
						mCodec.releaseOutputBuffer(frame.bufferIndex, false);
					} else if (mSurface.isValid()) {
						mCodec.releaseOutputBuffer(frame.bufferIndex, true);
						if (!mCheckAudioClockAfterResume) {
							mLastAudioTimeUs = currentClockTimeUs;
						}

						if (!mVideoRenderingStarted) {
							mVideoRenderingStarted = true;
							mCallback.obtainMessage(MSG_CODEC_NOTIFY,
									CODEC_VIDEO_RENDERING_START, 0)
									.sendToTarget();
						}
					} else {
						if (LOGS_ENABLED)
							Log.w(TAG, "No valid surface, release frame");
						mCodec.releaseOutputBuffer(frame.bufferIndex, false);
					}
				} catch (IllegalStateException e) {
					if (LOGS_ENABLED) {
						Log.e(TAG, "Codec error: Window(Surface) valid: "
								+ mSurface.isValid(), e);
					}
					mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
							MediaError.UNKNOWN).sendToTarget();
					return;
				}

				mNumRenderFrames++;
				frame = removeFirstDecodedFrame();
				addFrameToPool(frame);

				Frame nextFrame = peekDecodedFrame();
				delayMs = MAX_EARLY_FRAME_TIME_ALLOWED_MS;

				if (nextFrame != null && !mCheckAudioClockAfterResume) {
					delayMs = ((nextFrame.info.presentationTimeUs - mClock
							.getCurrentTimeUs()) / 1000);
					delayMs = (long) ((float) delayMs / mCurrentSpeed);
				}

				mRenderingHandler.sendEmptyMessageAtTime(MSG_RENDER,
						SystemClock.uptimeMillis() + delayMs);
			}
		}
	}

	private void doRenderOneFrame() {
		synchronized (mRenderingLock) {
			Frame frame = peekDecodedFrame();

			if (frame == null) {
				// No frames repost
				if (mRenderingHandler != null) {
					mRenderingHandler.sendEmptyMessageAtTime(
							MSG_RENDER_ONE_FRAME,
							SystemClock.uptimeMillis() + 100);
				}
				return;
			}

			try {
				mCodec.releaseOutputBuffer(frame.bufferIndex, true);
				mCallback.obtainMessage(MSG_CODEC_NOTIFY,
						CODEC_VIDEO_SEEK_COMPLETED,
						(int) (mClock.getCurrentTimeUs() / 1000) + 1)
						.sendToTarget();

			} catch (IllegalStateException e) {
				if (LOGS_ENABLED)
					Log.e(TAG, "Codec error", e);
				mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR,
						MediaError.UNKNOWN).sendToTarget();
				return;
			}

			mNumRenderFrames++;
			frame = removeFirstDecodedFrame();
			addFrameToPool(frame);
			mSeeking = false;

			if (mStarted && mEventHandler != null) {
				// Started during a seek.
				mEventHandler.sendEmptyMessage(MSG_START);
			}
		}
	}

	private void onAccept() {
		try {
			synchronized (mBindedLock) {
				if (!isBinded) {
					if (mServerSocket == null || !mServerSocket.isBound()) {
						Log.i(TAG, "before make server socket");
						mServerSocket = new ServerSocket(55000, 1);
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
				Log.i(TAG, "(onAccept)before accept");
				mSocket = mServerSocket.accept();
				Log.i(TAG, "(onAccept)before make output stream");
				mSocketOutputStream = new ObjectOutputStream(mSocket.getOutputStream());
				Log.i(TAG, "(onAccept)after make output stream");
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
				//Log.i(TAG, "before onWrite");
				onWrite();
				//Log.i(TAG, "after onWrite");
				break;
			case MSG_CLOSE:
				Log.i(TAG, "before onClose");
				onClose();
				Log.i(TAG, "after onClose");
				break;
			}
		}
	}

	@SuppressLint("HandlerLeak")
	class EventHandler extends Handler {

		public EventHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {

			switch (msg.what) {
			case MSG_SET_SOURCE:
				mSource = (MediaSource) msg.obj;
				break;
			case MSG_SET_SURFACE:
				mSurface = (Surface) msg.obj;
				break;
			case MSG_SETUP:
				doSetup((MediaFormat) msg.obj);
				break;
			case MSG_START:
				doStart();
				break;
			case MSG_PAUSE: {
				doPause();

				Handler replyHandler = (Handler) msg.obj;
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

				Handler replyHandler = (Handler) msg.obj;
				Message reply = replyHandler.obtainMessage();
				reply.obj = new Object();
				reply.sendToTarget();
				break;
			}
			case MSG_STOP: {
				doStop();

				Handler replyHandler = (Handler) msg.obj;
				Message reply = replyHandler.obtainMessage();
				reply.obj = new Object();
				reply.sendToTarget();
				break;
			}
			case MSG_SET_VIDEO_SCALING_MODE:
				mVideoScalingMode = msg.arg1;
				if (mCodec != null) {
					mCodec.setVideoScalingMode(msg.arg1);
				}
				break;
			default:
				if (LOGS_ENABLED)
					Log.w(TAG, "Unknown message");
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
			case MSG_RENDER_ONE_FRAME:
				doRenderOneFrame();
				break;
			default:
				if (LOGS_ENABLED)
					Log.w(TAG, "Unknown message");
				break;
			}
		}
	}
}