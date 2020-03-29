package com.ttsea.jcamera.demo.scan;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.ttsea.jcamera.callbacks.SimpleCameraCallback;
import com.ttsea.jcamera.core.CameraScanView;
import com.ttsea.jcamera.core.Constants;
import com.ttsea.jcamera.demo.R;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class ScanUI extends AppCompatActivity {
    private final String TAG = "CameraUI";

    private Activity mActivity;
    private Toolbar toolBar;

    private CameraScanView cameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan_ui);

        mActivity = this;

        toolBar = findViewById(R.id.toolBar);
        setSupportActionBar(toolBar);

        cameraView = findViewById(R.id.cameraView);

        cameraView.setCameraCallback(new SimpleCameraCallback() {
            @Override
            public void onCameraOpened() {
                Log.d(TAG, "onCameraOpened...");
                cameraView.setFlashMode(Constants.FLASH_OFF);
            }

            @Override
            public void onCameraError(int errorCode, String msg) {
                String errorMsg = "errorCode:" + errorCode + ", msg:" + msg;
                Log.e(TAG, "onCameraError, " + errorMsg);
                Toast.makeText(mActivity, errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void tryOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            cameraView.openCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 8);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 8:
                if (permissions.length != 1 || grantResults.length != 1) {
                    throw new RuntimeException("Error on requesting camera permission.");
                }
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "没有相机权限", Toast.LENGTH_SHORT).show();
                } else {
                    tryOpenCamera();
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tryOpenCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.releaseCamera();
    }
}
