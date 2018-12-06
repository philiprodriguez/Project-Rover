package xyz.philiprodriguez.projectroverserver;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.Collections;

import xyz.philiprodriguez.projectrovercommunications.ProjectRoverServer;
import xyz.philiprodriguez.projectrovercommunications.ServerSettings;

public class MainActivity extends AppCompatActivity {
    public static final String CLASS_IDENTIFIER = "MainActivity (Server)";
    private static final int CAMERA_PERMISSION_CODE = 315736;

    private ProjectRoverServer projectRoverServer;

    private Button btnStopServer;
    private Button btnTakePicture;
    private TextureView txvCameraPreview;

    // Camera shit only
    CameraManager cameraManager;
    String cameraRearId;
    Size cameraSize;
    HandlerThread cameraHandlerThread;
    Handler cameraBackgroundHandler;
    CameraDevice.StateCallback cameraStateCallback;
    CameraDevice cameraDevice;

    // Camera timer only
    Handler timerHandler;
    Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initComponents();
    }

    @Override
    protected void onResume() {
        super.onResume();

        openCameraBackgroundHandler();
        if (txvCameraPreview.isAvailable()) {
            setupCamera();
            openCamera();
        } else {
            txvCameraPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    setupCamera();
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onStop() {
        super.onStop();
        closeCamera();
        closeCameraBackgroundHandler();
    }

    private void closeCamera() {
//        if (cameraCaptureSession != null) {
//            cameraCaptureSession.close();
//            cameraCaptureSession = null;
//        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void closeCameraBackgroundHandler() {
        if (cameraBackgroundHandler != null) {
            cameraHandlerThread.quitSafely();
            cameraHandlerThread = null;
            cameraBackgroundHandler = null;
        }
    }

    private void initComponents() {
        btnStopServer = findViewById(R.id.btnStopServer_Main);
        btnTakePicture = findViewById(R.id.btnTakePicture_Main);
        txvCameraPreview = findViewById(R.id.txvCameraPreview_Main);

        btnStopServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (projectRoverServer == null || projectRoverServer.isKilled()) {
                    ServerSettings serverSettings = new ServerSettings();
                    projectRoverServer = new ProjectRoverServer(7345, serverSettings);
                    btnStopServer.setText("Stop Server");
                } else {
                    projectRoverServer.killServer();
                    projectRoverServer.waitForKillServer();
                    btnStopServer.setText("Start Server");
                }
            }
        });

        btnTakePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "This button does nothing!", Toast.LENGTH_SHORT).show();
                //projectRoverServer.doEnqueueImage(txvCameraPreview.getBitmap());
            }
        });

        timerHandler = new Handler();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (projectRoverServer != null)
                    projectRoverServer.doEnqueueImageAndRecycleBitmap(txvCameraPreview.getBitmap());
                timerHandler.postDelayed(timerRunnable, 50);
            }
        };
        timerHandler.postDelayed(timerRunnable, 50);
    }

    private void setupCamera() {
        System.out.println("Setting up camera...");

        cameraStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                System.out.println("Camera device populated!");
                cameraDevice = camera;
                createCaptureSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                System.out.println("Camera disconnected!");
                cameraDevice.close();
                cameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                System.out.println("Camera error!");
                cameraDevice.close();
                cameraDevice = null;
            }
        };


        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String[] allCameraIds = cameraManager.getCameraIdList();
            CameraCharacteristics cameraCharacteristics = null;
            for (String cameraId : allCameraIds) {
                cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);

                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraRearId = cameraId;
                    break;
                }
            }
            if (cameraRearId == null) {
                Toast.makeText(this, "No rear facing camera was found!", Toast.LENGTH_LONG).show();
                finish();
            } else {
                System.out.println("Got rear camera ID of " + cameraRearId);
            }

            // We have a camera with some camera characteristics!
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            for (Size size : streamConfigurationMap.getOutputSizes(SurfaceTexture.class)) {
                // Don't accept a size above 720p
                if (size.getWidth() * size.getHeight() <= 1280*720) {
                    Toast.makeText(this, "Using size " + size.toString(), Toast.LENGTH_SHORT).show();
                    cameraSize = size;
                    break;
                }
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to access camera!", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void openCamera() {
        System.out.println("Opening camera...");
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            } else {
                try {
                    cameraManager.openCamera(cameraRearId, cameraStateCallback, cameraBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to access camera for opening!", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        } else {
            Toast.makeText(this, "Android SDK level is too low to open camera!", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void openCameraBackgroundHandler() {
        System.out.println("Opening camera background handler");
        cameraHandlerThread = new HandlerThread("Camera Handler Thread");
        cameraHandlerThread.start();
        cameraBackgroundHandler = new Handler(cameraHandlerThread.getLooper());
    }

    private void createCaptureSession() {
        SurfaceTexture surfaceTexture = txvCameraPreview.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(cameraSize.getWidth(), cameraSize.getHeight());
        final Surface surface = new Surface(surfaceTexture);

        try {
            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        captureRequestBuilder.addTarget(surface);
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, cameraBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "Failed to access camera for capture request!", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    System.out.println("Configure failed!!!");
                }
            }, cameraBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to access camera for capture session!", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
