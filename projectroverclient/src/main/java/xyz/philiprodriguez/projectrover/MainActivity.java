package xyz.philiprodriguez.projectrover;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

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
}
