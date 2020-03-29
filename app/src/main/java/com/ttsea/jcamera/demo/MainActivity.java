package com.ttsea.jcamera.demo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.ttsea.jcamera.JCamera;
import com.ttsea.jcamera.demo.camera.CameraUI;
import com.ttsea.jcamera.demo.scan.ScanUI;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private Toolbar toolBar;

    private Button btnCamera;
    private Button btnScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolBar = findViewById(R.id.toolBar);
        setSupportActionBar(toolBar);

        JCamera.debugMode(true);

        btnCamera = findViewById(R.id.btnCamera);
        btnScan = findViewById(R.id.btnScan);

        btnCamera.setOnClickListener(this);
        btnScan.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent intent;
        switch (v.getId()) {
            case R.id.btnCamera:
                intent = new Intent(this, CameraUI.class);
                startActivity(intent);
                overridePendingTransition(R.anim.activity_in_from_bottom, 0);
                break;

            case R.id.btnScan:
                intent = new Intent(this, ScanUI.class);
                startActivity(intent);
                break;
        }
    }
}
