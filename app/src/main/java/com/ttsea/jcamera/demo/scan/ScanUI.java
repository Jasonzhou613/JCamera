package com.ttsea.jcamera.demo.scan;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.zxing.Result;
import com.ttsea.jcamera.callbacks.SimpleCameraCallback;
import com.ttsea.jcamera.core.CameraScanView;
import com.ttsea.jcamera.core.Constants;
import com.ttsea.jcamera.demo.R;
import com.ttsea.jcamera.demo.debug.JLog;
import com.ttsea.jcamera.demo.scan.zxing.ZXingDecoder;
import com.ttsea.jcamera.demo.utils.Utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class ScanUI extends AppCompatActivity {
    private Activity mActivity;

    private CameraScanView scanView;
    private ImageView ivPause;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan_ui);

        mActivity = this;

        scanView = findViewById(R.id.cameraView);
        ivPause = findViewById(R.id.ivPause);

        scanView.setCameraCallback(new SimpleCameraCallback() {
            @Override
            public void onCameraOpened() {
                JLog.d("onCameraOpened...");
                scanView.setFlashMode(Constants.FLASH_OFF);
            }

            @Override
            public void onCameraError(int errorCode, String msg) {
                String errorMsg = "errorCode:" + errorCode + ", msg:" + msg;
                JLog.e("onCameraError, " + errorMsg);
                Toast.makeText(mActivity, errorMsg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStartPreview() {
                super.onStartPreview();
                ivPause.setVisibility(View.GONE);
                scanView.setOneShotPreview();
            }

            @Override
            public void onStopPreview() {
                super.onStopPreview();
                ivPause.setVisibility(View.VISIBLE);
            }

            @Override
            public void oneShotFrameData(@Nullable byte[] data, int format, int width, int height) {
                decodeData(data, format, width, height);
            }
        });

        ivPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanView.startPreview();
            }
        });
    }

    private void tryOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            scanView.openCamera();
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
        scanView.releaseCamera();
    }

    private void decodeData(@Nullable byte[] data, int format, int width, int height) {
        final Result result = ZXingDecoder.getInstance().decodeData(data, scanView.getScanRect(), width, height);

        if (result != null && !Utils.isEmpty(result.getText())) {
            JLog.d("format:" + format + ", preSize:" + width + "x" + height + ", result:" + result);

            MediaPlayer.OnCompletionListener completionListener = new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer player) {
                    if (player != null) {
                        player.release();
                        player = null;
                    }
                }
            };
            Utils.playSound(mActivity, R.raw.beep, completionListener);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mActivity, result.getText(), Toast.LENGTH_LONG).show();
                    //scanView.stopPreview();
                }
            });

            scanView.stopPreview();

        } else {
            JLog.d("read data...");
            scanView.setOneShotPreview();
        }
    }
}
