package xyz.philiprodriguez.projectrover;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE = 54362;

    private EditText edtPort;
    private EditText edtHost;
    private Button btnConnect;
    private Toolbar tlb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initComponents();
    }

    private void initComponents() {
        edtHost = findViewById(R.id.edtHost_Main);
        edtPort = findViewById(R.id.edtPort_Main);
        btnConnect = findViewById(R.id.btnConnect_Main);
        tlb = findViewById(R.id.tlb_Main);

        setSupportActionBar(tlb);

        // Disable the connect button until we know we have the necessary app permissions!
        btnConnect.setEnabled(false);
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ConnectedActivity.class);
                intent.putExtra("host", edtHost.getText().toString());
                intent.putExtra("port", Integer.parseInt(edtPort.getText().toString()));
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (checkAllPermissions()) {
            btnConnect.setEnabled(true);
        } else {
            btnConnect.setEnabled(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    System.out.println("The required permission " + permissions[i] + " was rejected, so the app will not enable the connect button. Restart the app to try again.");
                    allGranted = false;
                }
            }
            if (allGranted) {
                System.out.println("All required permissions were granted! Enabling connection...");
                btnConnect.setEnabled(true);
            }
        }
    }

    // Returns true if all the needed permissions are already granted, and false otherwise.
    private boolean checkAllPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.INTERNET);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (missingPermissions.size() == 0) {
            return true;
        }
        System.out.println("Missing permissions: " + missingPermissions.toString());
        requestPermissions(missingPermissions.toArray(new String[]{}), PERMISSIONS_REQUEST_CODE);
        return false;
    }
}
