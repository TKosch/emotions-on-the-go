package com.example.test.openfaceandroid;

import android.content.res.AssetManager;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 *
 */
public class Wrapper
{
    private static final String TAG    = "OpenFace++";
    private static final String LIB    = "openface-jni";
    private static final int MINWIDTH  = 128;
    private static final int MINHEIGHT = 128;
    private static final int MAXWIDTH  = 1280;
    private static final int MAXHEIGHT = 960;

    static
    {
        // load native .so library
        System.loadLibrary(LIB);
    }

    /**
     * Can only be initialized once.
     */
    private static boolean initialized = false;

    /**
     * The last analysed values and frame.
     * Fields were updated in native code, when calling dequeue() with a True return.
     */
    public static final Result result = new Result();

    /**
     * Initializes the OpenFace++ engine
     * @param width Width of the input images
     * @param height Height of the input images
     * @param assetManager AssetManager to retrieve OpenFace++ assets from
     * @return True or False (see logs)
     */
    public static boolean init(
        final int width,
        final int height,
        @NonNull final AssetManager assetManager)
    {
        // don't init twice
        if (initialized)
        {
            Log.e(TAG, "Already initialized. Can't reinit.");
            return false;
        }

        // check frame size boundaries
        if (width  < MINWIDTH  || width  > MAXWIDTH ||
            height < MINHEIGHT || height > MAXHEIGHT)
        {
            Log.e(TAG, "Width or Height out of bound.");
            return false;
        }

        // create managed byte[] which receives pixeldata
        result.pixeldata = new byte[width * height * 4];

        // initialize native OpenFace++
        initialized = Init(width, height, result, assetManager);

        // init native openface++
        return initialized;
    }

    /**
     * Enqueues a new frame to process in OpenFace++
     * @param pixelData Pixel data in 'ImageFormat.NV21'
     * @return True or False (see logs)
     */
    public static boolean enqueue(@NonNull final byte[] pixelData)
    {
        if (!initialized)
            return false;

        return Enqueue(pixelData);
    }

    /**
     * Dequeues a possible result from OpenFace++.
     * The updated values are stored in 'Result' instance of this class.
     * @return True or False (= none available)
     */
    public static boolean dequeue()
    {
        if (!initialized)
            return false;

        return Dequeue();
    }

    /**
     * Returns the current FPS rate OpenFace++ is operating at
     * @return FPS
     */
    public static float getFPS()
    {
        if (!initialized)
            return 0.0f;

        return GetFPS();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static native boolean Init(int width, int height, Result result, AssetManager assetManager);
    private static native boolean Enqueue(byte[] dataArray);
    private static native boolean Dequeue();
    private static native float GetFPS();
}
