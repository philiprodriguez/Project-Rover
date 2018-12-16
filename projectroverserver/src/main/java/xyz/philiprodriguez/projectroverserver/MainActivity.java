package xyz.philiprodriguez.projectroverserver;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import xyz.philiprodriguez.projectrovercommunications.GlobalLogger;
import xyz.philiprodriguez.projectrovercommunications.MotorStateMessage;
import xyz.philiprodriguez.projectrovercommunications.OnLoggableEventListener;
import xyz.philiprodriguez.projectrovercommunications.OnMotorStateMessageReceivedListener;
import xyz.philiprodriguez.projectrovercommunications.OnServerSettingsMessageReceivedListener;
import xyz.philiprodriguez.projectrovercommunications.ProjectRoverServer;
import xyz.philiprodriguez.projectrovercommunications.ServerSettings;
import xyz.philiprodriguez.projectrovercommunications.ServerSettingsMessage;
import xyz.philiprodriguez.projectrovercommunications.ServerStateMessage;

public class MainActivity extends AppCompatActivity {
    public static final String CLASS_IDENTIFIER = "MainActivity (Server)";
    private static final int CAMERA_PERMISSION_CODE = 315736;
    public static final byte[] BLUETOOTH_START_SEQUENCE = new byte[]{'a', '8', 'f', 'e', 'J', '2', '9', 'p'};
    private static final int MAX_STATUS_TEXT_SIZE = 100;

    private ProjectRoverServer projectRoverServer;

    private final LinkedList<String> statusTexts = new LinkedList<String>();

    private ScrollView scrStatusScrollView;
    private TextView txtStatusMessage;
    private Button btnStopServer;
    private TextureView txvCameraPreview;

    // Camera shit only
    CameraManager cameraManager;
    String cameraRearId;
    Size cameraSize;
    HandlerThread cameraHandlerThread;
    Handler cameraBackgroundHandler;
    CameraDevice.StateCallback cameraStateCallback;
    CameraDevice cameraDevice;
    CaptureRequest.Builder captureRequestBuilder;
    CameraCaptureSession cameraCaptureSession;

    // Camera timer only
    Handler cameraTimerHandler;
    Runnable cameraTimerRunnable;

    // State send timer only
    Handler stateSendTimerHandler;
    Runnable stateSendTimerRunnble;

    // Bluetooth shit only
    BluetoothDevice bluetoothDevice;
    BluetoothSocket bluetoothSocket;
    Runnable bluetoothConnectRunnable;
    Handler bluetoothConnectionHandler;
    HandlerThread bluetoothConnectionHandlerThread;
    final BlockingQueue<byte[]> bluetoothSendQueue = new LinkedBlockingQueue<>();
    Thread bluetoothOutThread;

    private void setStatusAndLog(String message) {
        synchronized (statusTexts) {
            GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "Status: " + message);
            statusTexts.add(new Date().toString() + ": " + message);
            while (statusTexts.size() > MAX_STATUS_TEXT_SIZE) {
                statusTexts.removeFirst();
            }
            final StringBuffer sb = new StringBuffer();
            for (String s : statusTexts) {
                sb.append(s);
                sb.append(System.lineSeparator());
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    txtStatusMessage.setText(sb);
                    scrStatusScrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            scrStatusScrollView.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initComponents();
    }

    private void enqueueBluetoothMessage(byte[] everythingExceptStartSequence) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(BLUETOOTH_START_SEQUENCE);
            baos.write(everythingExceptStartSequence);
            bluetoothSendQueue.add(baos.toByteArray());
            baos.close();
        } catch (IOException e) {
            e.printStackTrace();
            setStatusAndLog("Unexpected issue enqueueing Bluetooth message!");
        }
    }

    private void initComponents() {
        scrStatusScrollView = findViewById(R.id.scrStatusScrollView_Main);
        txtStatusMessage = findViewById(R.id.txtStatusMessage_Main);
        btnStopServer = findViewById(R.id.btnStopServer_Main);
        txvCameraPreview = findViewById(R.id.txvCameraPreview_Main);

        btnStopServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (projectRoverServer == null || projectRoverServer.isKilled()) {
                    startServer();
                } else {
                    stopServer();
                }
            }
        });

        cameraTimerHandler = new Handler();
        cameraTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (projectRoverServer != null) {
                    projectRoverServer.doEnqueueImageAndRecycleBitmap(txvCameraPreview.getBitmap(225, 400));
                }
                cameraTimerHandler.postDelayed(cameraTimerRunnable, 20);
            }
        };
        cameraTimerHandler.postDelayed(cameraTimerRunnable, 20);

        stateSendTimerHandler = new Handler();
        stateSendTimerRunnble = new Runnable() {
            @Override
            public void run() {
                if (projectRoverServer != null) {
                    BatteryManager bm = (BatteryManager)getSystemService(BATTERY_SERVICE);
                    int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    projectRoverServer.doEnqueueServerStateMessage(new ServerStateMessage(System.currentTimeMillis(), batLevel));
                }
                stateSendTimerHandler.postDelayed(stateSendTimerRunnble, 5000);
            }
        };
        stateSendTimerHandler.postDelayed(stateSendTimerRunnble, 5000);
    }

    private void startServer() {
        setStatusAndLog("Starting server...");

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            setStatusAndLog("Cannot start since Wi-Fi is off!");
            return;
        }
        if  (wifiManager.getConnectionInfo().getNetworkId() == -1) {
            setStatusAndLog("Cannto start since Wi-Fi is disconnected!");
            return;
        }
        setStatusAndLog("Wi-Fi IP address is " + Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress()));

        projectRoverServer = new ProjectRoverServer(7345, new ServerSettings());
        projectRoverServer.setOnMotorStateMessageReceivedListener(new OnMotorStateMessageReceivedListener() {
            @Override
            public void OnMotorStateMessageReceived(MotorStateMessage message) {
                enqueueBluetoothMessage(new byte[]{'m', (byte)(message.getLeftForward()), (byte)(message.getLeftBackward()), (byte)(message.getRightForward()), (byte)(message.getRightBackward())});
            }
        });
        projectRoverServer.setOnLoggableEventListener(new OnLoggableEventListener() {
            @Override
            public void OnLoggableEvent(String message) {
                setStatusAndLog(message);
            }
        });
        projectRoverServer.setOnServerSettingsMessageReceivedListener(new OnServerSettingsMessageReceivedListener() {
            @Override
            public void OnServerSettingsMessageReceived(ServerSettingsMessage serverSettingsMessage) {
                try {
                    if (cameraCaptureSession != null && captureRequestBuilder != null) {
                        if (serverSettingsMessage.getServerSettings().getHeadlightOn()) {
                            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                        } else {
                            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                        }
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, cameraBackgroundHandler);
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                    setStatusAndLog("Failed to set server settings related to headlight! " + e.getMessage());
                }

                setStatusAndLog("Set server settings: " + serverSettingsMessage.getServerSettings().toString());
            }
        });
        setStatusAndLog("Server started with settings:" + System.lineSeparator() + projectRoverServer.getServerSettings().toString());
        btnStopServer.setText("Stop Server");
    }

    private void stopServer() {
        setStatusAndLog("Stopping server...");
        projectRoverServer.killServer();
        setStatusAndLog("Server stopped!");
        btnStopServer.setText("Start Server");
    }

    @Override
    protected void onResume() {
        super.onResume();

        openCameraBackgroundHandler();
        if (txvCameraPreview.isAvailable()) {
            if (setupCamera())
                openCamera();
        } else {
            txvCameraPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    if (setupCamera())
                        openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            });
        }

        openBluetoothHandler();
        if (getBluetoothDevice()) {
            connectBluetoothDevice();
        }

        startServer();
    }

    @Override
    protected void onPause() {
        super.onPause();

        closeCamera();
        closeCameraBackgroundHandler();

        closeBluetooth();
        closeBluetoothHandler();
        if (projectRoverServer != null) {
            stopServer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void openBluetoothHandler() {
        bluetoothConnectionHandlerThread = new HandlerThread("Bluetooth Connection Handler Thread");
        bluetoothConnectionHandlerThread.start();
        bluetoothConnectionHandler = new Handler(bluetoothConnectionHandlerThread.getLooper());
    }

    private void closeBluetoothHandler() {
        if (bluetoothConnectionHandler != null) {
            bluetoothConnectionHandlerThread.quitSafely();
            bluetoothConnectionHandlerThread = null;
            bluetoothConnectionHandler = null;
        }
    }

    private boolean getBluetoothDevice() {
        setStatusAndLog("Getting Bluetooth device..");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isEnabled()) {
                setStatusAndLog("Bluetooth is already enabled on the device...");
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                for (BluetoothDevice bluetoothDevice : pairedDevices) {
                    String deviceName = bluetoothDevice.getName();
                    String deviceMAC = bluetoothDevice.getAddress();

                    // TODO: make this dynamic
                    if (deviceMAC.equals("20:16:08:10:48:03")) {
                        setStatusAndLog("Found device " + deviceName + " with MAC " + deviceMAC);
                        this.bluetoothDevice = bluetoothDevice;
                        break;
                    }
                }
                if (this.bluetoothDevice != null) {
                    return true;
                } else {
                    setStatusAndLog("Failed to find suitable Bluetooth device! Make sure it is paired!");
                    return false;
                }
            } else {
                setStatusAndLog("Bluetooth is not enabled! Please enable bluetooth and restart the application...");
                return false;
            }
        } else {
            setStatusAndLog("Bluetooth is not supported on this device!");
            return false;
        }
    }

    private void connectBluetoothDevice() {
        setStatusAndLog("Attempting to connect to Bluetooth device...");
        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            bluetoothConnectRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        bluetoothSocket.connect();
                        openBluetoothOutThread();
                        setStatusAndLog("Bluetooth socket connected!");
                    } catch (IOException e) {
                        e.printStackTrace();
                        setStatusAndLog("Failed to connect the Bluetooth socket! Retrying...");
                        bluetoothConnectionHandler.post(bluetoothConnectRunnable);
                    }
                }
            };
            bluetoothConnectionHandler.post(bluetoothConnectRunnable);
        } catch (IOException e) {
            e.printStackTrace();
            setStatusAndLog("Failed to create socket for Bluetooth device!");
        }
    }

    private void openBluetoothOutThread() {
        setStatusAndLog("Opening Bluetooth send thread...");
        bluetoothOutThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    setStatusAndLog("Bluetooth out thread started!");
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            byte[] bytesToSend = bluetoothSendQueue.take();
                            //setStatusAndLog("Writing out bytes: " + Arrays.toString(bytesToSend));
                            bluetoothSocket.getOutputStream().write(bytesToSend);
                            bluetoothSocket.getOutputStream().flush();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                            setStatusAndLog("Bluetooth IO exception! " + e.getMessage());
                            break;
                        }
                    }
                    setStatusAndLog("Bluetooth out thread closing!");
                } finally {
                    // Try to reconnect Bluetooth!
                    setStatusAndLog("Attempting to re-establish Bluetooth connection!");
                    closeBluetooth();
                    closeBluetoothHandler();
                    openBluetoothHandler();
                    if (getBluetoothDevice()) {
                        connectBluetoothDevice();
                    }
                }
            }
        });
        bluetoothOutThread.start();
    }

    private void closeBluetooth() {
        setStatusAndLog("Closing Bluetooth socket...");
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
                this.bluetoothDevice = null;
                setStatusAndLog("Bluetooth socket closed!");
            } catch (IOException e) {
                setStatusAndLog("Failed to close Bluetooth socket!");
                e.printStackTrace();
            }
        }
    }

    private void openCameraBackgroundHandler() {
        setStatusAndLog("Opening camera background handler...");
        cameraHandlerThread = new HandlerThread("Camera Handler Thread");
        cameraHandlerThread.start();
        cameraBackgroundHandler = new Handler(cameraHandlerThread.getLooper());
    }

    private boolean setupCamera() {
        setStatusAndLog("Setting up camera...");

        cameraStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                setStatusAndLog("Camera device populated!");
                cameraDevice = camera;
                createCaptureSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                setStatusAndLog("Camera disconnected! Closing camera...");
                closeCamera();
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                setStatusAndLog("Camera error! Closing camera...");
                closeCamera();
            }
        };


        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            if (cameraManager == null) {
                setStatusAndLog("Failed to get camera manager");
                return false;
            }
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
                setStatusAndLog("No rear facing camera was found!");
                return false;
            } else {
                setStatusAndLog("Got rear camera ID of " + cameraRearId);
            }

            // We have a camera with some camera characteristics!
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            for (Size size : streamConfigurationMap.getOutputSizes(SurfaceTexture.class)) {
                // Don't accept a size above 720p
                if (size.getWidth() * size.getHeight() <= 1280*720) {
                    setStatusAndLog("Using camera size of " + size.toString());
                    cameraSize = size;
                    break;
                }
            }
            setStatusAndLog("Camera setup complete!");
            return true;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            setStatusAndLog("Failed to access camera!");
            return false;
        }
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

    private boolean openCamera() {
        setStatusAndLog("Opening camera...");
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                setStatusAndLog("Lacking required camera permissions! Requesting...");
                requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            } else {
                setStatusAndLog("Camera permission is granted!");
            }
        } else {
            setStatusAndLog("Android SDK level is too low to request permission to open camera!");
        }

        try {
            cameraManager.openCamera(cameraRearId, cameraStateCallback, cameraBackgroundHandler);
            setStatusAndLog("Camera successfully opened!");
            return true;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            setStatusAndLog("Failed to access camera for opening!");
            return false;
        }
    }

    private void createCaptureSession() {
        setStatusAndLog("Creating camera capture session...");
        SurfaceTexture surfaceTexture = txvCameraPreview.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(cameraSize.getWidth(), cameraSize.getHeight());
        final Surface surface = new Surface(surfaceTexture);

        try {
            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureSession = session;
                    try {
                        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
//                        captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f);
                        captureRequestBuilder.addTarget(surface);
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, cameraBackgroundHandler);
                        setStatusAndLog("Capture session created!");
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                        setStatusAndLog("Failed to access camera for capture request!");
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    setStatusAndLog("Configure failed!");
                }
            }, cameraBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            setStatusAndLog("Failed to access camera for capture session!");
        }
    }
}
