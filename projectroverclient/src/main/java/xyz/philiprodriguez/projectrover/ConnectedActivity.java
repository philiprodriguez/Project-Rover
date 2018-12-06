package xyz.philiprodriguez.projectrover;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;

import xyz.philiprodriguez.projectrovercommunications.GlobalLogger;
import xyz.philiprodriguez.projectrovercommunications.OnFrameReceivedListener;
import xyz.philiprodriguez.projectrovercommunications.ProjectRoverClient;

public class ConnectedActivity extends AppCompatActivity {
    public static final String CLASS_IDENTIFIER = "ConnectedActivity";

    private volatile ImageView imgCameraView;
    private volatile ProjectRoverClient projectRoverClient;
    private volatile Thread clienlandscapetThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connected);

        final int port = getIntent().getIntExtra("port", -1);
        final String host = getIntent().getStringExtra("host");
        clientThread = new Thread(new Runnable() {
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
                                        GlobalLogger.log(CLASS_IDENTIFIER, null, "Updated imgCameraView bitmap!");
                                        imgCameraView.setImageBitmap(bitmap);
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
                            Toast.makeText(ConnectedActivity.this, "Failed to connect to robot server!", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                }
            }
        });
        clientThread.start();

        initComponents();
    }

    private void initComponents() {
        imgCameraView = findViewById(R.id.imgCameraView_Connected);
    }
}
