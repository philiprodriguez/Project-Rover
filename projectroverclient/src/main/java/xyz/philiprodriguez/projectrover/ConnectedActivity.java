package xyz.philiprodriguez.projectrover;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.NoiseSuppressor;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import xyz.philiprodriguez.projectrovercommunications.ArmPositionMessage;
import xyz.philiprodriguez.projectrovercommunications.MotorStateMessage;
import xyz.philiprodriguez.projectrovercommunications.OnClientConnectionKilledListener;
import xyz.philiprodriguez.projectrovercommunications.OnFrameReceivedListener;
import xyz.philiprodriguez.projectrovercommunications.OnPCMFrameMessageReceivedListener;
import xyz.philiprodriguez.projectrovercommunications.OnServerStateMessageReceivedListener;
import xyz.philiprodriguez.projectrovercommunications.PCMFrameMessage;
import xyz.philiprodriguez.projectrovercommunications.ProjectRoverClient;
import xyz.philiprodriguez.projectrovercommunications.ServerSettings;
import xyz.philiprodriguez.projectrovercommunications.ServerSettingsMessage;
import xyz.philiprodriguez.projectrovercommunications.ServerStateMessage;

public class ConnectedActivity extends AppCompatActivity {
    public static final String CLASS_IDENTIFIER = "ConnectedActivity";

    private static final String ROBOT_TRIM_KEY = "robot_trim_key";

    private ImageView imgCameraView;
    private SeekBar sebUpDown;
    private SeekBar sebLeftRight;
    private TextView txtHUDInfo;
    private Button btnMenu;
    private Button btnSpeak;

    private LinearLayout llArmXYZ;
    private TrackpadView tpvArmXY;
    private TrackpadView tpvArmZ;
    private TextView txtArmXYZ;
    private float armXf, armYf, armZf;

    // Represents the last time an arm update was sent in milliseconds.
    // Used to ignore arm inputs that occur less than soem number of ms from the previous input.
    private AtomicLong lastArmUpdate = new AtomicLong(0);

    // Represents the last "accepted"/sent value read from sebUpDown.
    private AtomicInteger lastUpDown = new AtomicInteger(50);

    // Represents the last "accepted"/sent value read from sebLeftRight.
    private AtomicInteger lastLeftRight = new AtomicInteger(50);

    // Represents a counter for the number of SeekBars that are currently being touched.
    // Used to send a stop message whenever this number goes to 0 (user releases all controls).
    private AtomicInteger numTracking = new AtomicInteger(0);

    // Simple variables to store the host and port that were sent in the intent to this activity
    private volatile int port;
    private volatile String host;

    private volatile ProjectRoverClient projectRoverClient;
    private volatile Handler clientConnectorHandler;
    private volatile HandlerThread clientConnectorHandlerThread;
    private volatile Runnable clientConnectorRunnable;

    private final double armEpspilon = 0.003; // Require change of 3 mm to send
    private float lastX = 0.0f;
    private float lastY = 0.0f;
    private float lastZ = 0.0f;

    private SharedPreferences sharedPreferences;

    // Audio stuff for playback of audio from the server
    private final Queue<Byte> audioPlaybackPcmValueQueue = new ConcurrentLinkedQueue<>();
    private volatile Handler audioPlaybackHandler;
    private volatile HandlerThread audioPlaybackHandlerThread;
    private volatile Runnable audioPlaybackRunnable;
    private volatile AudioTrack audioPlaybackTrack;

    // Audio stuff for recording audio to be sent to the server
    private volatile AudioRecord audioRecord;
    private volatile Handler audioRecordHandler;
    private volatile HandlerThread audioRecordHandlerThread;
    private volatile Runnable audioRecordRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connected);

        this.port = getIntent().getIntExtra("port", -1);
        this.host = getIntent().getStringExtra("host");

        this.sharedPreferences = getPreferences(Context.MODE_PRIVATE);

        initComponents();
    }

    @Override
    protected void onResume() {
        super.onResume();

        startAudioPlaybackHandler();

        openClientConnectionHandler();
        connect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        closeClientConnectorHandler();
        stopAudioPlaybackHandler();

        // Kill client connection
        if (projectRoverClient != null) {
            projectRoverClient.killClientConnection();
            projectRoverClient.waitForKillClientConnection();
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    private void initComponents() {
        imgCameraView = findViewById(R.id.imgCameraView_Connected);
        sebUpDown = findViewById(R.id.sebUpDown_Connected);
        sebLeftRight = findViewById(R.id.sebLeftRight_Connected);
        txtHUDInfo = findViewById(R.id.txtHUDInfo_Connected);
        btnMenu = findViewById(R.id.btnMenu_Connected);
        btnSpeak = findViewById(R.id.btnSpeak_Connected);
        llArmXYZ = findViewById(R.id.llArmXYZ_Connected);
        tpvArmXY = findViewById(R.id.tpvArmXY_Connected);
        tpvArmZ = findViewById(R.id.tpvArmZ_Connected);
        txtArmXYZ = findViewById(R.id.txtArmXYZ_Connected);

        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popupMenu = new PopupMenu(ConnectedActivity.this, btnMenu);
                popupMenu.getMenuInflater().inflate(R.menu.connected_menu, popupMenu.getMenu());

                if (projectRoverClient == null) {
                    for (int i = 0; i < popupMenu.getMenu().size(); i++) {
                        popupMenu.getMenu().getItem(i).setEnabled(false);
                    }
                }

                popupMenu.getMenu().getItem(0).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        ServerSettings pss = projectRoverClient.getPerceivedServerSettings();
                        pss.setHeadlightOn(!pss.getHeadlightOn());
                        projectRoverClient.doEnqueueServerSettingsMessage(new ServerSettingsMessage(System.currentTimeMillis(), pss));
                        return true;
                    }
                });
                popupMenu.getMenu().getItem(1).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (llArmXYZ.getVisibility() == View.VISIBLE) {
                            llArmXYZ.setVisibility(View.GONE);
                            txtArmXYZ.setVisibility(View.GONE);

                            sebLeftRight.setVisibility(View.VISIBLE);
                            sebUpDown.setVisibility(View.VISIBLE);
                        } else {
                            llArmXYZ.setVisibility(View.VISIBLE);
                            txtArmXYZ.setVisibility(View.VISIBLE);

                            sebLeftRight.setVisibility(View.GONE);
                            sebUpDown.setVisibility(View.GONE);
                        }
                        return true;
                    }
                });
                popupMenu.getMenu().getItem(2).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        showSettingsDialog();
                        return true;
                    }
                });

                popupMenu.show();
            }
        });

        btnSpeak.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    stopAudioPlaybackHandler();
                    startAudioRecordHandler();
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    stopAudioRecordHandler();
                    startAudioPlaybackHandler();
                }
                return true;
            }
        });

        sebUpDown.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (Math.abs(progress-lastUpDown.get()) > 5) {
                    lastUpDown.set(progress);
                    sendUpdate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                numTracking.incrementAndGet();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sebUpDown.setProgress(50);
                int amountTracking = numTracking.decrementAndGet();

                if (amountTracking <= 0) {
                    sendStopUpdate();
                }
            }
        });

        sebLeftRight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (Math.abs(progress-lastLeftRight.get()) > 5) {
                    lastLeftRight.set(progress);
                    sendUpdate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                numTracking.incrementAndGet();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sebLeftRight.setProgress(50);
                int amountTracking = numTracking.decrementAndGet();

                if (amountTracking <= 0) {
                    sendStopUpdate();
                }
            }
        });

        tpvArmXY.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (tpvArmXY.getWidth() == 0) {
                    System.out.println("Ignored zero call...");
                    return;
                }
                tpvArmXY.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                tpvArmXY.setX(tpvArmXY.getWidth()/2.0f);
                tpvArmXY.setY(tpvArmXY.getHeight()/2.0f);
                armXf = 0f;
                armYf = 0.15f;
                armZf = 0.15f;
                sendArmUpdate(true);

                tpvArmXY.setOnTrackpadPositionChangedListener(new OnTrackpadPositionChangedListener() {
                    @Override
                    public void onTrackpadPositionChanged(float x, float y) {
                        // Get x and y as percentages of half lengths with (0,0) at bottom center
                        float xp = ((x-(tpvArmXY.getWidth()/2.0f))/tpvArmXY.getWidth())*2.0f;
                        float yp = (tpvArmXY.getHeight()-y)/tpvArmXY.getHeight();

                        // Now scale to meters, where max is 300mm
                        xp = 0.3f*xp;
                        yp = 0.3f*yp;

                        armXf = xp;
                        armYf = yp;

                        sendArmUpdate(false);
                    }
                });
            }
        });
        tpvArmZ.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (tpvArmZ.getWidth() == 0) {
                    System.out.println("Ignored zero call...");
                    return;
                }
                tpvArmZ.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                tpvArmZ.setY(tpvArmZ.getHeight()/3.0f);

                tpvArmZ.setOnTrackpadPositionChangedListener(new OnTrackpadPositionChangedListener() {
                    @Override
                    public void onTrackpadPositionChanged(float x, float y) {
                        // Get z as percentage
                        float zp = ((tpvArmZ.getHeight()-y)-(tpvArmZ.getHeight()/3.0f))/tpvArmZ.getHeight();
                        zp = 0.45f*zp;

                        armZf = zp;

                        sendArmUpdate(false);
                    }
                });
            }
        });

        audioPlaybackTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                44100,
                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_8BIT,
                8820,
                AudioTrack.MODE_STREAM);

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.CAMCORDER,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_8BIT,
                44100);
        NoiseSuppressor noiseSuppressorRecord = NoiseSuppressor.create(audioRecord.getAudioSessionId());
        if (noiseSuppressorRecord != null)
            noiseSuppressorRecord.setEnabled(true);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("AudioRecord object failed to initialize properly! Check the constructor args?");
        }
    }

    private void connect() {
        if (projectRoverClient != null && !projectRoverClient.isKilled()) {
            return;
        }

        clientConnectorHandler.post(clientConnectorRunnable);
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
                        byte[] playbackBytes;
                        if (audioPlaybackPcmValueQueue.size() > 4410 * 5) {
                            System.out.println("Speeding up");
                            // If we're more than 5 tenths of a second behind, then let's speed it up by
                            // playing only 9 out of every 10 bytes.
                            playbackBytes = new byte[3969];
                            int place = 0;
                            for (int i = 0; i < 4410; i++) {
                                byte val = audioPlaybackPcmValueQueue.poll();
                                if (i % 10 == 0) {
                                    continue;
                                }
                                playbackBytes[place] = val;
                                place++;
                            }
                            if (place != playbackBytes.length) {
                                throw new IllegalStateException("Place was " + place + " when it should have been " + playbackBytes.length);
                            }
                        } else {
                            // If we're not falling behind, just play it back normally
                            playbackBytes = new byte[4410];
                            for (int i = 0; i < 4410; i++) {
                                playbackBytes[i] = audioPlaybackPcmValueQueue.poll();
                            }
                        }
                        audioPlaybackTrack.write(playbackBytes, 0, playbackBytes.length, AudioTrack.WRITE_BLOCKING);
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

    private void startAudioRecordHandler() {
        audioRecordHandlerThread = new HandlerThread("Audio Record Handler Thread");
        audioRecordHandlerThread.start();
        audioRecordHandler = new Handler(audioRecordHandlerThread.getLooper());

        audioRecordRunnable = new Runnable() {
            @Override
            public void run() {
                // Our audio frame size is one tenth of one second...
                byte[] audioFrameBytes = new byte[4410];

                // Make sure we're actually recording!
                if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.startRecording();
                }

                // We can use READ_BLOCKING since we have our own handler / thread here..
                audioRecord.read(audioFrameBytes, 0, audioFrameBytes.length, AudioRecord.READ_BLOCKING);

                if (projectRoverClient != null) {
                    projectRoverClient.doEnqueueAudioFrame(audioFrameBytes);
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

    private void openClientConnectionHandler() {
        clientConnectorHandlerThread = new HandlerThread("Client Connector Handler Thread");
        clientConnectorHandlerThread.start();
        clientConnectorHandler = new Handler(clientConnectorHandlerThread.getLooper());

        clientConnectorRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    projectRoverClient = new ProjectRoverClient(host, port);
                    projectRoverClient.setOnFrameReceivedListener(new OnFrameReceivedListener() {
                        @Override
                        public void OnFrameReceived(final Bitmap bitmap) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (imgCameraView != null) {
                                        imgCameraView.setImageBitmap(bitmap);
                                    }
                                }
                            });
                        }
                    });
                    projectRoverClient.setOnPCMFrameMessageReceivedListener(new OnPCMFrameMessageReceivedListener() {
                        @Override
                        public void onPCMFrameMessageReceived(PCMFrameMessage message) {
                            // Enqueue all PCM values
                            if (audioPlaybackHandler != null) {
                                for (byte b : message.getFrameBytes()) {
                                    audioPlaybackPcmValueQueue.add(b);
                                }
                            }
                        }
                    });
                    projectRoverClient.setOnClientConnectionKilledListener(new OnClientConnectionKilledListener() {
                        @Override
                        public void OnClientConnectionKilled() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(ConnectedActivity.this, "Connection terminated...", Toast.LENGTH_SHORT).show();
                                    if (clientConnectorHandler != null) {
                                        clientConnectorHandler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                connect();
                                            }
                                        }, 10000);
                                    }
                                }
                            });
                        }
                    });
                    projectRoverClient.setOnServerStateMessageReceivedListener(new OnServerStateMessageReceivedListener() {
                        @Override
                        public void OnServerStateMessageReceived(final ServerStateMessage serverStateMessage) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("Robot Primary Battery: " + serverStateMessage.getPrimaryBatteryLevel() + "%");
                                    sb.append(System.lineSeparator());
                                    sb.append("Robot Tablet Battery: " + serverStateMessage.getPhoneBatteryLevel() + "%");
                                    txtHUDInfo.setText(sb.toString());
                                }
                            });
                        }
                    });
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ConnectedActivity.this, "Connected to robot!", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ConnectedActivity.this, "Failed to connect to robot server! Retrying in 10 seconds...", Toast.LENGTH_SHORT).show();

                            if (clientConnectorHandler != null) {
                                clientConnectorHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        connect();
                                    }
                                }, 10000);
                            }
                        }
                    });
                }
            }
        };
    }

    private void closeClientConnectorHandler() {
        if (clientConnectorHandlerThread != null) {
            clientConnectorHandlerThread.quitSafely();
            clientConnectorHandlerThread = null;
            clientConnectorHandler = null;
        }
    }

    private void sendArmUpdate(boolean force) {
        if (!force && System.currentTimeMillis()-lastArmUpdate.get() < 50) {
            // Ignore
            return;
        } else {
            lastArmUpdate.set(System.currentTimeMillis());
        }

        // Update string
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(armXf);
        sb.append(", ");
        sb.append(armYf);
        sb.append(", ");
        sb.append(armZf);
        sb.append(")");
        txtArmXYZ.setText(sb.toString());

        // Send to server if distance enough
        double dist = Math.sqrt(Math.pow(armXf-lastX, 2.0)+Math.pow(armYf-lastY, 2.0)+Math.pow(armZf-lastZ, 2.0));
        if (force || dist > armEpspilon) {
            if (projectRoverClient != null) {
                projectRoverClient.doEnqueueArmPositionMessage(new ArmPositionMessage(System.currentTimeMillis(), armXf, armYf, armZf));
                lastX = armXf;
                lastY = armYf;
                lastZ = armZf;
            }
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Robot Settings");

        LayoutInflater layoutInflater = this.getLayoutInflater();
        View rootView = layoutInflater.inflate(R.layout.dialog_server_settings, null);

        Switch headlightOn = rootView.findViewById(R.id.switchHeadlightOn);
        SeekBar jpegQuality = rootView.findViewById(R.id.sebJpegQuality);
        SeekBar servoRot = rootView.findViewById(R.id.sebServoRot);

        // Keep in mind that robotTrim is actually a client-side setting, not a server-side setting.
        final TextView robotTrimText = rootView.findViewById(R.id.txtRobotTrim);
        SeekBar robotTrim = rootView.findViewById(R.id.sebRobotTrim);

        headlightOn.setChecked(projectRoverClient.getPerceivedServerSettings().getHeadlightOn());
        jpegQuality.setProgress(projectRoverClient.getPerceivedServerSettings().getJpegQuality());
        servoRot.setProgress(projectRoverClient.getPerceivedServerSettings().getServoRotationAmount());

        // Since robotTrim is client-side, load from shared preferences.
        // default to 10, the middle.
        robotTrimText.setText("Trim (" + (sharedPreferences.getInt(ROBOT_TRIM_KEY, 10)-10) + ")");
        robotTrim.setProgress(sharedPreferences.getInt(ROBOT_TRIM_KEY, 10));

        headlightOn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (projectRoverClient != null) {
                    ServerSettings pss = projectRoverClient.getPerceivedServerSettings();
                    pss.setHeadlightOn(isChecked);
                    projectRoverClient.doEnqueueServerSettingsMessage(new ServerSettingsMessage(System.currentTimeMillis(), pss));
                }
            }
        });

        jpegQuality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (projectRoverClient != null) {
                    ServerSettings pss = projectRoverClient.getPerceivedServerSettings();
                    pss.setJpegQuality(seekBar.getProgress());
                    projectRoverClient.doEnqueueServerSettingsMessage(new ServerSettingsMessage(System.currentTimeMillis(), pss));
                }
            }
        });

        servoRot.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (projectRoverClient != null) {
                    ServerSettings pss = projectRoverClient.getPerceivedServerSettings();
                    pss.setServoRotationAmount(seekBar.getProgress());
                    projectRoverClient.doEnqueueServerSettingsMessage(new ServerSettingsMessage(System.currentTimeMillis(), pss));
                }
            }
        });

        robotTrim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                robotTrimText.setText("Trim (" + (i-10) + ")");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Save client setting
                sharedPreferences.edit().putInt(ROBOT_TRIM_KEY, seekBar.getProgress()).apply();
            }
        });

        builder.setView(rootView);
        builder.setNegativeButton("Dismiss", null);
        AlertDialog created = builder.create();
        created.show();
    }

    private void sendUpdate() {
        if (projectRoverClient == null)
            return;

        // Fetch current values and apply trim for left/right
        int lastUpDownLocal = lastUpDown.get();
        int lastLeftRightLocal =  Math.max(0, Math.min(100, lastLeftRight.get() + sharedPreferences.getInt(ROBOT_TRIM_KEY, 10)-10));
        System.out.println("lastUpDownLocal=" + lastUpDownLocal);
        System.out.println("lastLeftRightLocal=" + lastLeftRightLocal);

        double magnitudeUpDown = Math.abs(lastUpDownLocal-50)/50.0;
        double magnitudeLeftRight = Math.abs(lastLeftRightLocal-50)/50.0;
        boolean goingUp = lastUpDownLocal > 50;
        boolean goingLeft = lastLeftRightLocal < 50;

        int lf = 0;
        int lb = 0;
        int rf = 0;
        int rb = 0;

        if (goingUp) {
            lf = 255;
            rf = 255;

            // Apply left right skew
            if (goingLeft) {
                lf -= (int)(magnitudeLeftRight*255.0);
            } else {
                rf -= (int)(magnitudeLeftRight*255.0);
            }

            // Scale
            lf *= magnitudeUpDown;
            rf *= magnitudeUpDown;

            projectRoverClient.doEnqueueMotorStateMessage(new MotorStateMessage(System.currentTimeMillis(), lf, lb, rf, rb));
        } else {
            lb = 255;
            rb = 255;

            // Apply left right skew
            if (goingLeft) {
                lb -= (int)(magnitudeLeftRight*255.0);
            } else {
                rb -= (int)(magnitudeLeftRight*255.0);
            }

            // Scale
            lb *= magnitudeUpDown;
            rb *= magnitudeUpDown;

            projectRoverClient.doEnqueueMotorStateMessage(new MotorStateMessage(System.currentTimeMillis(), lf, lb, rf, rb));
        }
    }

    private void sendStopUpdate() {
        if (projectRoverClient != null)
            projectRoverClient.doEnqueueMotorStateMessage(new MotorStateMessage(System.currentTimeMillis(), 0, 0, 0, 0));
    }
}
