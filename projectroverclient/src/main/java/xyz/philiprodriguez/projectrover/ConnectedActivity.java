package xyz.philiprodriguez.projectrover;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import xyz.philiprodriguez.projectrovercommunications.MotorStateMessage;
import xyz.philiprodriguez.projectrovercommunications.OnClientConnectionKilledListener;
import xyz.philiprodriguez.projectrovercommunications.OnFrameReceivedListener;
import xyz.philiprodriguez.projectrovercommunications.ProjectRoverClient;

public class ConnectedActivity extends AppCompatActivity {
    public static final String CLASS_IDENTIFIER = "ConnectedActivity";

    private volatile ImageView imgCameraView;
    private SeekBar sebUpDown;
    private SeekBar sebLeftRight;

    private volatile int port;
    private volatile String host;

    private volatile ProjectRoverClient projectRoverClient;
    private volatile Handler clientConnectorHandler;
    private volatile HandlerThread clientConnectorHandlerThread;
    private volatile Runnable clientConnectorRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connected);

        this.port = getIntent().getIntExtra("port", -1);
        this.host = getIntent().getStringExtra("host");

        initComponents();
    }

    private void connect() {
        if (projectRoverClient != null && !projectRoverClient.isKilled()) {
            return;
        }

        clientConnectorHandler.post(clientConnectorRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();

        openClientConnectionHandler();
        connect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        closeClientConnectorHandler();

        // Kill client connection
        if (projectRoverClient != null) {
            projectRoverClient.killClientConnection();
            projectRoverClient.waitForKillClientConnection();
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

    private AtomicInteger lastUpDown = new AtomicInteger(50);
    private AtomicInteger lastLeftRight = new AtomicInteger(50);
    private AtomicInteger numTracking = new AtomicInteger(0);
    private void initComponents() {
        imgCameraView = findViewById(R.id.imgCameraView_Connected);
        sebUpDown = findViewById(R.id.sebUpDown_Connected);
        sebLeftRight = findViewById(R.id.sebLeftRight);

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
    }

    private void sendUpdate() {
        if (projectRoverClient == null)
            return;

        double magnitudeUpDown = Math.abs(lastUpDown.get()-50)/50.0;
        double magnitudeLeftRight = Math.abs(lastLeftRight.get()-50)/50.0;
        boolean goingUp = lastUpDown.get() > 50;
        boolean goingLeft = lastLeftRight.get() < 50;

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
