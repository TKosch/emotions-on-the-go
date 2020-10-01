package com.example.test.openfaceandroid;

import android.Manifest;
import android.annotation.TargetApi;
import android.appwidget.AppWidgetHost;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.graphics.ImageFormat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 *
 */
public final class MainActivity extends AppCompatActivity {
    private final static int WIDTH = 640;
    private final static int HEIGHT = 480;
    private final static int PIXELBYTES = 4;
    private final static int BYTESIZE = WIDTH * HEIGHT * PIXELBYTES;
    private final static int RESULTSPAN = 33;
    private final static int PERMISSION_REQUEST = 1;
    private final static Handler handler = new Handler(Looper.getMainLooper());
    private final static Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    private final static byte[][] buffers = new byte[2][BYTESIZE];
    private int LOG_INTERVAL = 990;

    private static boolean isPaused;
    private static boolean hasPermissions; // evtl für später noch

    private ImageView vImageView;

    private static final String TAG = "AndroidCamera2API";
    private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /////////////// CAMERA 2 Start
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader mImageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private String mCameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        //textureView.setSurfaceTextureListener(textureListener);
        vImageView = (ImageView) findViewById(R.id.frame);
        openCamera();
        Wrapper.init(WIDTH, HEIGHT, getResources().getAssets());
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            Log.e(TAG, "onSurfaceTextureAvailable");
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.e(TAG, "onError");
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    protected void startBackgroundThread() {
        Log.e(TAG, "startBackgroundThread");
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        Log.e(TAG, "stopBackgroundThread");
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                //initCamera();
                finish();
            } else
                Wrapper.init(WIDTH, HEIGHT, getResources().getAssets());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
        Log.e(TAG, "onResume");
        startBackgroundThread();
        handler.postDelayed(checkResult, RESULTSPAN);
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        isPaused = true;
        //closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    protected void createCameraPreview() {
        Log.e(TAG, "createCameraPreview");
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            cameraDevice.createCaptureSession(Arrays.asList( mImageReader.getSurface()), new CameraCaptureSession.StateCallback() { //surface,
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "openCamera()");
        try {
            for (String cameraId : manager.getCameraIdList()) {
                //cameraId = manager.getCameraIdList()[1];
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (!(facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)) {
                    continue;
                }

                mImageReader = ImageReader.newInstance(WIDTH, HEIGHT,
                        ImageFormat.YUV_420_888, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
                // Add permission for camera and let user grant the permission
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                    return;
                }
                manager.openCamera(cameraId, stateCallback, null);

                mCameraId = cameraId;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
        }
    }

    private final Runnable checkResult = new Runnable() {
        @Override
        public final void run() {
            if (Wrapper.dequeue() &&
                    Wrapper.result.pixeldata != null &&
                    Wrapper.result.pixeldata.length >= BYTESIZE) {
                final ByteBuffer bytebuf = ByteBuffer.wrap(Wrapper.result.pixeldata);
                // show last frame
                bitmap.copyPixelsFromBuffer(bytebuf);
                vImageView.setImageBitmap(bitmap);
            }

            // LOGGING
            LOG_INTERVAL -= RESULTSPAN;
            if (LOG_INTERVAL <= 0.0) {
                // Check if camera is open:

                Log.d(TAG, "A,H,N,S,att,face\n"
                        + Wrapper.result.emotFloatAngry
                        + "," + Wrapper.result.emotFloatHappy
                        + "," + Wrapper.result.emotFloatNeutral
                        + "," + Wrapper.result.emotFloatSad
                        + "," + Wrapper.result.attention
//                        + "," + camUse
                        + "," + Wrapper.result.faceDetected);
                LOG_INTERVAL = 990;
            }

            // schedule next check
            // 33ms = ~30fps max output fps
            if (!isPaused)
                handler.postDelayed(checkResult, RESULTSPAN);
        }
    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image != null) {

                byte[] data = convertYUV420ToNV21_ALL_PLANES(image);// forward pixeldata to native openface++

                byte[] data2 = rotateNV21(data, WIDTH, HEIGHT, 270); // bei rotation squeeze issue
                Wrapper.enqueue(data2); //TEST ROBIN
                image.close();
            }
        }
    };
    /////////////// CAMERA 2 END

    public static byte[] rotateNV21_2(final byte[] yuv,
                                      final int width,
                                      final int height,
                                      final int rotation) {
        if (rotation == 0) return yuv;
        if (rotation % 90 != 0 || rotation < 0 || rotation > 270) {
            throw new IllegalArgumentException("0 <= rotation < 360, rotation % 90 == 0");
        }

        final byte[] output = new byte[yuv.length];
        final int frameSize = width * height;
        final boolean swap = rotation % 180 != 0;
        final boolean xflip = rotation % 270 != 0;
        final boolean yflip = rotation >= 180;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                final int yIn = j * width + i;
                final int uIn = frameSize + (j >> 1) * width + (i & ~1);
                final int vIn = uIn + 1;

                final int wOut = swap ? height : width;
                final int hOut = swap ? width : height;
                final int iSwapped = swap ? j : i;
                final int jSwapped = swap ? i : j;
                final int iOut = xflip ? wOut - iSwapped - 1 : iSwapped;
                final int jOut = yflip ? hOut - jSwapped - 1 : jSwapped;

                final int yOut = jOut * wOut + iOut;
                final int uOut = frameSize + (jOut >> 1) * wOut + (iOut & ~1);
                final int vOut = uOut + 1;

                output[yOut] = (byte) (0xff & yuv[yIn]);
                output[uOut] = (byte) (0xff & yuv[uIn]);
                output[vOut] = (byte) (0xff & yuv[vIn]);
            }
        }
        return output;
    }

    private byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y luma
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i--;
            }
        }
        return yuv;
    }


    public static byte[] rotateY12toYUV420(byte[] input, int width, int height, int rotation) {
        byte[] output = new byte[input.length];
        boolean swap = (rotation == 90 || rotation == 270);
        boolean flip = (rotation == 90 || rotation == 180);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int xo = x, yo = y;
                int w = width, h = height;
                int xi = xo, yi = yo;
                if (swap) {
                    xi = w * yo / h;
                    yi = h * xo / w;
                }
                if (flip) {
                    xi = w - xi - 1;
                    yi = h - yi - 1;
                }
                output[w * yo + xo] = input[w * yi + xi];
                int fs = w * h;
                int qs = (fs >> 2);
                xi = (xi >> 1);
                yi = (yi >> 1);
                xo = (xo >> 1);
                yo = (yo >> 1);
                w = (w >> 1);
                h = (h >> 1);
                // adjust for interleave here
                int ui = fs + (w * yi + xi) * 2;
                int uo = fs + w * yo + xo;
                // and here
                int vi = ui + 1;
                int vo = qs + uo;
                output[uo] = input[vi];
                output[vo] = input[ui];
            }
        }
        return output;
    }

    public static byte[] rotateNV21(byte[] input, int width, int height, int rotation) {
        byte[] output = new byte[input.length];
        boolean swap = (rotation == 90 || rotation == 270);
        boolean yflip = true;// (rotation == 90 || rotation == 180);
        boolean xflip = (rotation == 270 || rotation == 180);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int xo = x, yo = y;
                int w = width, h = height;
                int xi = xo, yi = yo;
                if (swap) {
                    xi = w * yo / h;
                    yi = h * xo / w;
                }
                if (yflip) {
                    yi = h - yi - 1;
                }
                if (xflip) {
                    xi = w - xi - 1;
                }
                output[w * yo + xo] = input[w * yi + xi];
                int fs = w * h;
                int qs = (fs >> 2);
                xi = (xi >> 1);
                yi = (yi >> 1);
                xo = (xo >> 1);
                yo = (yo >> 1);
                w = (w >> 1);
                h = (h >> 1);
                // adjust for interleave here
                int ui = fs + (w * yi + xi) * 2;
                int uo = fs + (w * yo + xo) * 2;
                // and here
                int vi = ui + 1;
                int vo = uo + 1;
                output[uo] = input[ui];
                output[vo] = input[vi];
            }
        }
        return output;
    }

    private byte[] DataArrayToRGBApixels(byte[] data) {
        byte[] bits = new byte[data.length * 4]; //That's where the RGBA array goes.
        int i;
        for (i = 0; i < data.length; i++) {
            bits[i * 4] =
                    bits[i * 4 + 1] =
                            bits[i * 4 + 2] = (byte) ~data[i]; //Invert the source bits
            bits[i * 4 + 3] = (byte) 0xff; // the alpha.
        }
        return bits;
    }

    private byte[] convertYUV420ToNV21(Image imgYUV420) {
        byte[] rez;

        ByteBuffer buffer0 = imgYUV420.getPlanes()[0].getBuffer();
        ByteBuffer buffer2 = imgYUV420.getPlanes()[2].getBuffer();
        int buffer0_size = buffer0.remaining();
        int buffer2_size = buffer2.remaining();
        rez = new byte[buffer0_size + buffer2_size];

        buffer0.get(rez, 0, buffer0_size);
        buffer2.get(rez, buffer0_size, buffer2_size);

        return rez;
    }

    private byte[] convertYUV420ToNV21_ALL_PLANES(Image imgYUV420) {

        byte[] rez;

        ByteBuffer buffer0 = imgYUV420.getPlanes()[0].getBuffer();
        ByteBuffer buffer1 = imgYUV420.getPlanes()[1].getBuffer();
        ByteBuffer buffer2 = imgYUV420.getPlanes()[2].getBuffer();

        int buffer0_size = buffer0.remaining();
        int buffer1_size = buffer1.remaining();
        int buffer2_size = buffer2.remaining();

        byte[] buffer0_byte = new byte[buffer0_size];
        byte[] buffer1_byte = new byte[buffer1_size];
        byte[] buffer2_byte = new byte[buffer2_size];
        buffer0.get(buffer0_byte, 0, buffer0_size);
        buffer1.get(buffer1_byte, 0, buffer1_size);
        buffer2.get(buffer2_byte, 0, buffer2_size);


        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // swap 1 and 2 as blue and red colors are swapped
            outputStream.write(buffer0_byte);
            outputStream.write(buffer2_byte);
            outputStream.write(buffer1_byte);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        rez = outputStream.toByteArray();

        return rez;
    }

    /////////////// ROBIN TEST FUNKTIONEN END
}
