package org.cocos2dx.lib;

import org.m4m.IProgressListener;
import org.m4m.domain.Resolution;
import org.m4m.android.graphics.FullFrameTexture;
import org.m4m.android.graphics.FrameBuffer;
import org.m4m.android.graphics.EglUtil;

import android.opengl.GLES20;
import android.os.Environment;
import android.util.Log;
import android.content.Context;
import android.graphics.Bitmap;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.File;
import java.nio.ByteBuffer;
import java.io.FileOutputStream;
import java.lang.Thread;
import java.nio.ByteOrder;

public class Capturing
{
    private static final String TAG = "Capturing";

    private static FullFrameTexture texture;
    private FrameBuffer frameBuffer;

    private VideoCapture videoCapture;
    private int width = 0;
    private int height = 0;

    private int videoWidth = 0;
    private int videoHeight = 0;
    private int videoFrameRate = 0;

    private long nextCaptureTime = 0;
    private long startTime = 0;

    private static Capturing instance = null;

    private SharedContext sharedContext = null;
    private EncodeThread encodeThread = null;
    private boolean finalizeFrame = false;
    private boolean isRunning = false;

    private IProgressListener progressListener = new IProgressListener() {
        @Override
        public void onMediaStart() {
            startTime = System.nanoTime();
            nextCaptureTime = 0;
            encodeThread.start();
            isRunning = true;
        }

        @Override
        public void onMediaProgress(float progress) {
        }

        @Override
        public void onMediaDone() {
        }

        @Override
        public void onMediaPause() {
        }

        @Override
        public void onMediaStop() {
        }

        @Override
        public void onError(Exception exception) {
        }
    };

    private class EncodeThread extends Thread
    {
        private static final String TAG = "EncodeThread";

        private SharedContext sharedContext;
        private boolean isStopped = false;
        private boolean newFrameIsAvailable = false;
        private FrameBuffer encodeFrameBuffer;

        EncodeThread(SharedContext sharedContext, int width, int height) {
            super();
            this.sharedContext = sharedContext;
            encodeFrameBuffer = new FrameBuffer(EglUtil.getInstance());
            encodeFrameBuffer.setResolution(new Resolution(width, height));
        }

        @Override
        public void run() {
            int textureID = encodeFrameBuffer.getTextureId();
            while (!isStopped) {
                if (newFrameIsAvailable) {
                    synchronized (videoCapture) {
                        Log.d(TAG, "videoCapture capturing " + textureID);
                        sharedContext.makeCurrent();
                        videoCapture.beginCaptureFrame();
                        GLES20.glViewport(0, 0, videoWidth, videoHeight);
                        texture.draw(textureID);
                        videoCapture.endCaptureFrame();
                        newFrameIsAvailable = false;
                        sharedContext.doneCurrent();
                    }
                }
            }
            isStopped = false;
            synchronized (videoCapture) {
                Log.d(TAG, "videoCapture.stop");
                videoCapture.stop();
            }
        }


        public void queryStop() {
            isStopped = true;
        }

        public void pushFrame(int textureID) {
            // Render to intermediate FBO
            encodeFrameBuffer.bind();
            Log.d(TAG, "pushFrame " + textureID);
            texture.draw(textureID);
            encodeFrameBuffer.unbind();
            newFrameIsAvailable = true;
        }
    }

    public Capturing(Context context, int width, int height)
    {
        float arenaRatio = height/width;

        videoCapture = new VideoCapture(context, progressListener);

        frameBuffer = new FrameBuffer(EglUtil.getInstance());
        frameBuffer.setResolution(new Resolution(width, height));
        this.width = width;
        this.height = height;
        Log.d(TAG, "constructor " + context);
        texture = new FullFrameTexture();
        sharedContext = new SharedContext();
        instance = this;
    }

    public static Capturing getInstance()
    {
        return instance;
    }

    public static String getDirectoryDCIM()
    {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + File.separator;
    }

    public void initCapturing(int width, int height, int frameRate, int bitRate)
    {
        videoFrameRate = frameRate;
        VideoCapture.init(width, height, frameRate, bitRate);
        videoWidth = width;
        videoHeight = height;

        encodeThread = new EncodeThread(sharedContext, width, height);

    }
    public void initCapturing(int width, int height,int screenWidth,int screenHeight, int frameRate, int bitRate)
    {
        videoFrameRate = frameRate;
        VideoCapture.init(width, height, frameRate, bitRate);
        videoWidth = width;
        videoHeight = height;

        encodeThread = new EncodeThread(sharedContext, width, height);

    }
    public void startCapturing(final String videoPath)
    {
        if (videoCapture == null) {
            return;
        }
        (new Thread() {
            public void run() {
                synchronized (videoCapture) {
                    try {
                        videoCapture.start(videoPath);
                    } catch (IOException e) {
                        Log.e(TAG, "--- startCapturing error");
                    }
                }
            }
        }).start();
    }

    public void beginCaptureFrame()
    {
        if(!isRunning)
            return;
        long elapsedTime = System.nanoTime() - startTime;
        if (elapsedTime >= nextCaptureTime) {
            finalizeFrame = true;
            frameBuffer.bind();
            Log.d(TAG, "binded texture " + frameBuffer.getTextureId());
            nextCaptureTime += 1000000000 / videoFrameRate;
        }
    }

    public void captureFrame(int textureID)
    {
        // Submit new frame
        encodeThread.pushFrame(textureID);
        // Restore viewport
        GLES20.glViewport(0, 0, width, height);
    }

    public void endCaptureFrame()
    {
        if (!finalizeFrame)
            return;

        frameBuffer.unbind();
        int textureID = frameBuffer.getTextureId();

        Log.d("Capturing", "context " + sharedContext);
        texture.draw(textureID);

        captureFrame(textureID);

        finalizeFrame = false;
    }

    public void stopCapturing()
    {
        isRunning = false;

        if (finalizeFrame) {
            finalizeFrame = false;
        }
        encodeThread.queryStop();
    }

    public boolean isRunning()
    {
        return isRunning;
    }

    public void savePreview (String previewFile) {
        int width = videoWidth;
        int height = videoHeight;
        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        GLES20.glGetError();
        buf.rewind();

        try {
            BufferedOutputStream bos = null;
            File file = new File(previewFile);
            file.createNewFile();
            bos = new BufferedOutputStream(new FileOutputStream(file));
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buf);
            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
            bmp.recycle();
            if (bos != null)
                bos.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "Saved " + width + "x" + height + " frame as '" + previewFile + "'");
    }
}