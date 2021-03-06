package xyz.philiprodriguez.projectroverserver;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.NoiseSuppressor;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import xyz.philiprodriguez.projectrovercommunications.ArmPositionMessage;
import xyz.philiprodriguez.projectrovercommunications.GlobalLogger;
import xyz.philiprodriguez.projectrovercommunications.MotorStateMessage;
import xyz.philiprodriguez.projectrovercommunications.OnArmPositionMessageReceivedListener;
import xyz.philiprodriguez.projectrovercommunications.OnLoggableEventListener;
import xyz.philiprodriguez.projectrovercommunications.OnMotorStateMessageReceivedListener;
import xyz.philiprodriguez.projectrovercommunications.OnPCMFrameMessageReceivedListener;
import xyz.philiprodriguez.projectrovercommunications.OnServerSettingsMessageReceivedListener;
import xyz.philiprodriguez.projectrovercommunications.PCMFrameMessage;
import xyz.philiprodriguez.projectrovercommunications.ProjectRoverServer;
import xyz.philiprodriguez.projectrovercommunications.ServerSettings;
import xyz.philiprodriguez.projectrovercommunications.ServerSettingsMessage;
import xyz.philiprodriguez.projectrovercommunications.ServerStateMessage;

public class MainActivity extends AppCompatActivity {
    public static final String CLASS_IDENTIFIER = "MainActivity (Server)";
    private static final int PERMISSIONS_REQUEST_CODE = 315736;
    private static final int MAX_STATUS_TEXT_SIZE = 100;

    private ProjectRoverServer projectRoverServer;

    private final LinkedList<String> statusTexts = new LinkedList<String>();

    private ScrollView scrStatusScrollView;
    private TextView txtStatusMessage;
    private Button btnStopServer;
    private TextureView txvCameraPreview;

    // Bluetooth
    BluetoothHandler bluetoothHandler;

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

    // Audio stuff for recording and sending to client
    AudioRecord audioRecord;
    Handler audioRecordHandler;
    HandlerThread audioRecordHandlerThread;
    Runnable audioRecordRunnable;

    // Audio stuff for playback of audio from the client
    final Queue<Short> audioPlaybackPcmValueQueue = new ConcurrentLinkedQueue<>();
    Handler audioPlaybackHandler;
    HandlerThread audioPlaybackHandlerThread;
    Runnable audioPlaybackRunnable;
    AudioTrack audioPlaybackTrack;

    // State send timer only
    Handler stateSendTimerHandler;
    Runnable stateSendTimerRunnble;

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    setStatusAndLog("The required permission " + permissions[i] + " was rejected, so the app will not resume. Restart the app to try again.");
                    allGranted = false;
                }
            }
            if (allGranted) {
                setStatusAndLog("All required permissions were granted! Resuming...");
                performOnResumeDuties();
            }
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
                    projectRoverServer.doEnqueueImageAndRecycleBitmap(txvCameraPreview.getBitmap(360, 480));
                }
                cameraTimerHandler.postDelayed(cameraTimerRunnable, 25);
            }
        };
        cameraTimerHandler.postDelayed(cameraTimerRunnable, 25);
    }

    // Returns true if all the needed permissions are already granted, and false otherwise.
    private boolean checkAllPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.INTERNET);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_WIFI_STATE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.BLUETOOTH);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (missingPermissions.size() == 0) {
            return true;
        }
        setStatusAndLog("Missing permissions: " + missingPermissions.toString());
        requestPermissions(missingPermissions.toArray(new String[]{}), PERMISSIONS_REQUEST_CODE);
        return false;
    }

    private void startServer() {
        setStatusAndLog("Starting server...");

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            setStatusAndLog("Cannot start since Wi-Fi is off!");
            return;
        }
        if  (wifiManager.getConnectionInfo().getNetworkId() == -1) {
            setStatusAndLog("Cannot start since Wi-Fi is disconnected!");
            return;
        }
        setStatusAndLog("Wi-Fi IP address is " + Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress()));

        projectRoverServer = new ProjectRoverServer(7345, new ServerSettings());
        projectRoverServer.setOnMotorStateMessageReceivedListener(new OnMotorStateMessageReceivedListener() {
            @Override
            public void OnMotorStateMessageReceived(MotorStateMessage message) {
                bluetoothHandler.enqueueBluetoothMessage(new byte[]{'m', (byte)(message.getLeftForward()), (byte)(message.getLeftBackward()), (byte)(message.getRightForward()), (byte)(message.getRightBackward())});
            }
        });
        projectRoverServer.setOnArmPositionMessageReceivedListener(new OnArmPositionMessageReceivedListener() {
            @Override
            public void OnArmPositionMessageReceived(ArmPositionMessage message) {
                setStatusAndLog("Got arm position message: " + message.toString());

                // Determine necessary thetas to satisfy arm position request
                ArmDriver armDriver = new ArmDriver(0.15f, 0.15f, 15);
                long start = System.currentTimeMillis();
                double[] thetas = armDriver.getThetas((float)message.getX(), (float)message.getY(), (float)message.getZ(), 0.005); // no more than 5mm off
                long end = System.currentTimeMillis();

                if (thetas == null) {
                    // Cannot make the requested point!
                    setStatusAndLog("Requested point out of bounds!!!");
                    return;
                }

                setStatusAndLog("Thetas are: " + Arrays.toString(thetas));
                setStatusAndLog("Which provide (" + armDriver.x_2(thetas[0], thetas[1], thetas[2]) + ", " + armDriver.y_2(thetas[0], thetas[1], thetas[2]) + ", " + armDriver.z_2(thetas[0], thetas[1], thetas[2]));
                setStatusAndLog("Took " + (end-start) + "ms");

                ByteBuffer byteBuffer = ByteBuffer.allocate(1+4*3);
                byteBuffer.put((byte)'a');
                byteBuffer.putFloat((float)thetas[0]);
                byteBuffer.putFloat((float)thetas[1]);
                byteBuffer.putFloat((float)thetas[2]);
                bluetoothHandler.enqueueBluetoothMessage(byteBuffer.array());
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

                bluetoothHandler.enqueueBluetoothMessage(new byte[]{'s', (byte)(serverSettingsMessage.getServerSettings().getServoRotationAmount() + 5)});

                setStatusAndLog("Set server settings: " + serverSettingsMessage.getServerSettings().toString());
            }
        });
        projectRoverServer.setOnPCMFrameMessageReceivedListener(new OnPCMFrameMessageReceivedListener() {
            @Override
            public void onPCMFrameMessageReceived(PCMFrameMessage message) {
                // Enqueue all PCM values
                if (audioPlaybackHandler != null) {
                    for (short val : message.getPCMValues()) {
                        audioPlaybackPcmValueQueue.add(val);
                    }
                }
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

    // Constants for battery stuff
    private final double a = -0.00000015515;
    private final double b = 3.77174;
    private final double c = 0.0000000001152;
    private final double d = 0.00000518042;
    private final double g = -1.00089;
    private final double h = 1.00001;

    @Override
    protected void onResume() {
        super.onResume();

        setStatusAndLog("Checking permissions...");
        if (checkAllPermissions()) {
            setStatusAndLog("All required permissions are granted!");
            performOnResumeDuties();
        } else {
            setStatusAndLog("Cannot resume since permissions check failed! Will retry once permissions are granted.");
            return;
        }
    }

    private void performOnResumeDuties() {
        if (audioPlaybackTrack == null) {
            audioPlaybackTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    44100,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    8820,
                    AudioTrack.MODE_STREAM);
        }

        if (audioRecord == null) {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.CAMCORDER,
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    44100);
            NoiseSuppressor noiseSuppressorRecord = NoiseSuppressor.create(audioRecord.getAudioSessionId());
            if (noiseSuppressorRecord != null)
                noiseSuppressorRecord.setEnabled(true);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioRecord object failed to initialize properly! Check the constructor args?");
            }
        }

        startAudioRecordHandler();
        startAudioPlaybackHandler();

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

        bluetoothHandler = new BluetoothHandler("20:16:08:10:48:03", new OnBluetoothConnectedCallback() {
            @Override
            public void onBluetoothConnected(BluetoothHandler bluetoothHandler) {

            }
        }, new OnBluetoothDisconnectedCallback() {
            @Override
            public void onBluetoothDisconnected(BluetoothHandler handler) {

            }
        }, new OnLoggableEventListener() {
            @Override
            public void OnLoggableEvent(String message) {
                setStatusAndLog(message);
            }
        });

        // All the stuff for starting the send state timer here
        stateSendTimerHandler = new Handler();
        stateSendTimerRunnble = new Runnable() {
            @Override
            public void run() {
                if (projectRoverServer != null) {
                    // First request from the teensy our robot's battery level!
                    bluetoothHandler.addOnVoltageValueReceivedCallback(new OnVoltageValueReceivedCallback() {
                        @Override
                        public void onVoltageValueReceived(int voltageValue) {
                            // Get tablet's battery level too
                            BatteryManager bm = (BatteryManager)getSystemService(BATTERY_SERVICE);
                            int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

                            // Magic based on Physics 2 for the voltage divider & regression to AGM charge chart
                            final double x = voltageValue - 560.0;
                            final double primaryBatLevel = Math.pow(a*Math.pow(x, b)+c*Math.pow(x, 5)+d*Math.pow(x,3)+g*x+h*x, 2) * 100.0;
                            GlobalLogger.log(CLASS_IDENTIFIER, GlobalLogger.INFO, "primaryBatLevel is " + primaryBatLevel + " from x of " + x);
                            final int primaryBatLevelInt = Math.min((int)Math.round(primaryBatLevel), 100);

                            projectRoverServer.doEnqueueServerStateMessage(new ServerStateMessage(System.currentTimeMillis(), batLevel, primaryBatLevelInt));
                        }
                    });
                    bluetoothHandler.enqueueBluetoothMessage(new byte[]{'v'});
                }

                stateSendTimerHandler.postDelayed(stateSendTimerRunnble, 1000);
            }
        };
        stateSendTimerHandler.postDelayed(stateSendTimerRunnble, 1000);

        startServer();
    }

    private void startAudioRecordHandler() {
        audioRecordHandlerThread = new HandlerThread("Audio Record Handler Thread");
        audioRecordHandlerThread.start();
        audioRecordHandler = new Handler(audioRecordHandlerThread.getLooper());

        audioRecordRunnable = new Runnable() {
            @Override
            public void run() {
                // Our audio frame size is one tenth of one second...
                short[] audioFrameVals = new short[4410];

                // Make sure we're actually recording!
                if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.startRecording();
                }

                // We can use READ_BLOCKING since we have our own handler / thread here..
                audioRecord.read(audioFrameVals, 0, audioFrameVals.length, AudioRecord.READ_BLOCKING);

                if (projectRoverServer != null) {
                    projectRoverServer.doEnqueueAudioFrame(audioFrameVals);
                }

                Handler localHandlerCopy = audioRecordHandler;
                if (localHandlerCopy != null) {
                    audioRecordHandler.postDelayed(audioRecordRunnable, 1);
                }
            }
        };
        audioRecordHandler.postDelayed(audioRecordRunnable, 1);
    }

    private void stopAudioRecordHandler() {
        if (audioRecordHandlerThread != null) {
            audioRecordHandlerThread.quitSafely();
            audioRecordHandlerThread = null;
            audioRecordHandler = null;
            audioRecord.stop();
        }
    }

    private void startAudioPlaybackHandler() {
        // Any time we are re-starting playback, we should nuke any values currently in the queue...
        audioPlaybackPcmValueQueue.clear();
        audioPlaybackHandlerThread = new HandlerThread("Client Audio Handler Thread");
        audioPlaybackHandlerThread.start();
        audioPlaybackHandler = new Handler(audioPlaybackHandlerThread.getLooper());

        audioPlaybackRunnable = new Runnable() {
            @Override
            public void run() {
                if (audioPlaybackPcmValueQueue.size() > 0) {
                    // Only play if we have at least a tenth of a second of audio.
                    if (audioPlaybackPcmValueQueue.size() >= 4410) {
                        short[] playbackVals;
                        if (audioPlaybackPcmValueQueue.size() > 4410 * 5) {
                            System.out.println("Speeding up");
                            // If we're more than 5 tenths of a second behind, then let's speed it up by
                            // playing only 9 out of every 10 bytes.
                            playbackVals = new short[3969];
                            int place = 0;
                            for (int i = 0; i < 4410; i++) {
                                short val = audioPlaybackPcmValueQueue.poll();
                                if (i % 10 == 0) {
                                    continue;
                                }
                                playbackVals[place] = val;
                                place++;
                            }
                            if (place != playbackVals.length) {
                                throw new IllegalStateException("Place was " + place + " when it should have been " + playbackVals.length);
                            }
                        } else {
                            // If we're not falling behind, just play it back normally
                            playbackVals = new short[4410];
                            for (int i = 0; i < 4410; i++) {
                                playbackVals[i] = audioPlaybackPcmValueQueue.poll();
                            }
                        }
                        audioPlaybackTrack.write(playbackVals, 0, playbackVals.length, AudioTrack.WRITE_BLOCKING);
                    }

                    if (audioPlaybackTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                        audioPlaybackTrack.play();
                    }
                }

                Handler localHandlerRef = audioPlaybackHandler;
                if (localHandlerRef != null) {
                    localHandlerRef.postDelayed(audioPlaybackRunnable, 1);
                }
            }
        };

        // Kick off the audio playback thread...
        audioPlaybackHandler.postDelayed(audioPlaybackRunnable, 1);
    }

    private void stopAudioPlaybackHandler() {
        if (audioPlaybackHandlerThread != null) {
            audioPlaybackHandlerThread.quitSafely();
            audioPlaybackHandlerThread = null;
            audioPlaybackHandler = null;
            audioPlaybackTrack.pause();
            audioPlaybackTrack.flush();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopAudioPlaybackHandler();
        stopAudioRecordHandler();

        closeCamera();
        closeCameraBackgroundHandler();

        // All the stuff for stopping the send state timer here
        if (stateSendTimerHandler != null) {
            stateSendTimerHandler.removeCallbacksAndMessages(null);
            stateSendTimerHandler = null;
            stateSendTimerRunnble = null;
        }

        if (bluetoothHandler != null) {
            bluetoothHandler.recycle();
        }

        if (projectRoverServer != null) {
            stopServer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void openCameraBackgroundHandler() {
        setStatusAndLog("Opening camera background handler...");
        cameraHandlerThread = new HandlerThread("Camera Handler Thread");
        cameraHandlerThread.start();
        cameraBackgroundHandler = new Handler(cameraHandlerThread.getLooper());
    }

    private boolean setupCamera() {
        setStatusAndLog("Setting up camera...");

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
            Size bestSizeSoFar = null;
            for (Size size : streamConfigurationMap.getOutputSizes(SurfaceTexture.class)) {
                // Use the largest 4:3 that is smaller than 1920*1080 pixels.
                if (size.getWidth()*size.getHeight() <= 1920*1080
                        && Math.abs((size.getWidth()/(double)size.getHeight()) - (4.0/3.0)) <= 0.01
                        && (bestSizeSoFar == null || bestSizeSoFar.getWidth()*bestSizeSoFar.getHeight() < size.getWidth()*size.getHeight())) {
                    bestSizeSoFar = size;
                    break;
                }
            }
            setStatusAndLog("Using camera size of " + bestSizeSoFar.toString());
            cameraSize = bestSizeSoFar;

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

        try {
            cameraManager.openCamera(cameraRearId, cameraStateCallback, cameraBackgroundHandler);
            setStatusAndLog("Camera successfully opened!");
            return true;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            setStatusAndLog("Failed to access camera for opening!");
            return false;
        } catch (SecurityException e) {
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
