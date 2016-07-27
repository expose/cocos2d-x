package org.cocos2dx.lib;

import android.content.Context;
import android.util.Log;

import org.m4m.*;
import org.m4m.android.AndroidMediaObjectFactory;
import org.m4m.android.AudioFormatAndroid;
import org.m4m.android.VideoFormatAndroid;

import java.io.IOException;

public class VideoCapture
{
    private static final String TAG = "VideoCapture";

    private static final String Codec = "video/avc";
    private static int IFrameInterval = 1;

    private static final Object syncObject = new Object();
    private static volatile VideoCapture videoCapture;

    private static VideoFormat videoFormat;
    private static int videoWidth;
    private static int videoHeight;
    private GLCapture capturer;

    private boolean isConfigured;
    private boolean isStarted;
    private long framesCaptured;
    private Context context;
    private IProgressListener progressListener;

    public VideoCapture(Context context, IProgressListener progressListener)
    {
        this.context = context;
        this.progressListener = progressListener;
    }

    public static void init(int width, int height, int frameRate, int bitRate)
    {
        videoWidth = width;
        videoHeight = height;

        videoFormat = new VideoFormatAndroid(Codec, videoWidth, videoHeight);
        videoFormat.setVideoFrameRate(frameRate);
        videoFormat.setVideoBitRateInKBytes(bitRate);
        videoFormat.setVideoIFrameInterval(IFrameInterval);
    }

    public void start(String videoPath) throws IOException
    {
        if (isStarted())
            throw new IllegalStateException(TAG + " already started!");
        Log.d(TAG, "context: " + context);
        capturer = new GLCapture(new AndroidMediaObjectFactory(context), progressListener);
        capturer.setTargetFile(videoPath);
        capturer.setTargetVideoFormat(videoFormat);

     //   AudioFormat audioFormat = new AudioFormatAndroid("audio/mp4a-latm", 44100, 2);
     //   capturer.setTargetAudioFormat(audioFormat);

        capturer.start();

        isStarted = true;
        isConfigured = false;
        framesCaptured = 0;
    }

    public void stop()
    {
        if (!isStarted())
            throw new IllegalStateException(TAG + " not started or already stopped!");

        try {
            capturer.stop();
            isStarted = false;
        } catch (Exception ex) {
            Log.e(TAG, "--- Exception: GLCapture can't stop");
        }

        capturer = null;
        isConfigured = false;
    }

    private void configure()
    {
        if (isConfigured())
            return;

        try {
            capturer.setSurfaceSize(videoWidth, videoHeight);
            isConfigured = true;
        } catch (Exception ex) {
        }
    }

    public void beginCaptureFrame()
    {
        if (!isStarted())
            return;

        configure();
        if (!isConfigured())
            return;

        capturer.beginCaptureFrame();
    }

    public void endCaptureFrame()
    {
        if (!isStarted() || !isConfigured())
            return;

        capturer.endCaptureFrame();
        framesCaptured++;
    }

    public boolean isStarted()
    {
        return isStarted;
    }

    public boolean isConfigured()
    {
        return isConfigured;
    }

}