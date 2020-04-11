package com.ttsea.jcamera.demo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.ttsea.jcamera.JCamera;
import com.ttsea.jcamera.demo.camera.CameraUI;
import com.ttsea.jcamera.demo.scan.ScanUI;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

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
        switch (v.getId()) {
            case R.id.btnCamera:
                openCameraUI();
                break;

            case R.id.btnScan:
                openScanUI();
                break;
        }
    }

    private void openCameraUI() {
        String[] permissions = new String[]{Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO};
        ActivityCompat.requestPermissions(this, permissions, 8);
    }

    private void openScanUI() {
        String[] permissions = new String[]{Manifest.permission.CAMERA};
        ActivityCompat.requestPermissions(this, permissions, 10);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 8:
            case 10:
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        if (permissions[i].equals(Manifest.permission.CAMERA)) {
                            Toast.makeText(this, "没有相机权限", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "没有录音权限", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                }
                Intent intent;
                if (requestCode == 8) {
                    intent = new Intent(this, CameraUI.class);
                    startActivity(intent);
                    overridePendingTransition(R.anim.activity_in_from_bottom, 0);

                } else if (requestCode == 10) {
                    intent = new Intent(this, ScanUI.class);
                    startActivity(intent);
                }
                break;
        }
    }

}
