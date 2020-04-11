package com.ttsea.jcamera.demo.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import com.ttsea.jcamera.annotation.Flash;
import com.ttsea.jcamera.callbacks.SimpleCameraCallback;
import com.ttsea.jcamera.core.AspectRatio;
import com.ttsea.jcamera.core.CameraPreview;
import com.ttsea.jcamera.core.Constants;
import com.ttsea.jcamera.demo.R;
import com.ttsea.jcamera.demo.camera.adapter.MyAdapter;
import com.ttsea.jcamera.demo.camera.model.ItemEntity;
import com.ttsea.jcamera.demo.camera.model.SaveStatus;
import com.ttsea.jcamera.demo.camera.views.RecordButton;
import com.ttsea.jcamera.demo.debug.JLog;
import com.ttsea.jcamera.demo.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class CameraUI extends AppCompatActivity implements RecordButton.GestureCallback {
    private final String TAG = "CameraUI";

    private Activity mActivity;
    private Toolbar toolBar;
    private Menu mMenu;

    private CameraPreview cameraView;
    private ImageView ivClose;
    private RecordButton btnRecord;

    private boolean cameraOpened;
    private SaveStatus saveStatus;

    private File videoFile;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_ui);

        mActivity = this;

        toolBar = findViewById(R.id.toolBar);
        setSupportActionBar(toolBar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }

        cameraView = findViewById(R.id.cameraView);

        ivClose = findViewById(R.id.ivClose);
        btnRecord = findViewById(R.id.btnRecord);

        btnRecord.setCallback(this);

        ivClose.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onBackKeyClicked();
                return true;
            }
        });

        cameraView.setCameraCallback(new SimpleCameraCallback() {
            @Override
            public void onCameraOpened() {
                JLog.d(TAG, "onCameraOpened...");
                cameraOpened = true;
                saveStatus.facing = cameraView.getFacing();

                cameraView.setFlashMode(saveStatus.flash);
                cameraView.setAspectRatio(saveStatus.ratio);

                invalidateOptionsMenu();
            }

            @Override
            public void onCameraClosed() {
                JLog.d(TAG, "onCameraClosed...");
                cameraOpened = false;
                invalidateOptionsMenu();
            }

            @Override
            public void onCameraError(int errorCode, String msg) {
                String errorMsg = "errorCode:" + errorCode + ", msg:" + msg;
                JLog.e(TAG, "onCameraError, " + errorMsg);
                Toast.makeText(mActivity, errorMsg, Toast.LENGTH_SHORT).show();
                cameraOpened = false;
                //finish();
            }

            @Override
            public void onPictureTaken(@Nullable File file, String errorMsg) {
                JLog.d(TAG, "onPictureTaken...");

                if (file == null) {
                    //Toast.makeText(mActivity, errorMsg, Toast.LENGTH_SHORT).show();
                    return;
                }

                String msg = "图片已保存至:" + file.getAbsolutePath();
                Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRecordError(String errorMsg) {
                Toast.makeText(mActivity, errorMsg, Toast.LENGTH_SHORT).show();
                videoFile = null;
            }
        });

        saveStatus = new SaveStatus();
        saveStatus.facing = cameraView.getFacing();
        saveStatus.flash = cameraView.getFlashMode();
        saveStatus.ratio = cameraView.getAspectRatio();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.openCamera(saveStatus.facing);
    }

    @Override
    protected void onPause() {
        super.onPause();

        saveStatus = new SaveStatus();
        saveStatus.facing = cameraView.getFacing();
        saveStatus.flash = cameraView.getFlashMode();
        saveStatus.ratio = cameraView.getAspectRatio();

        cameraView.releaseCamera();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (outState == null) {
            outState = new Bundle();
        }

        outState.putInt("facing", saveStatus.facing);
        outState.putInt("flash", saveStatus.flash);
        outState.putParcelable("ratio", saveStatus.ratio);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            AspectRatio ratio = savedInstanceState.getParcelable("ratio");
            if (ratio != null) {
                saveStatus.facing = savedInstanceState.getInt("facing", Constants.FACING_BACK);
                saveStatus.flash = savedInstanceState.getInt("flash", Constants.FLASH_OFF);
                saveStatus.ratio = ratio;
            }
        }
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!cameraOpened) {
            mMenu = null;
            return super.onCreateOptionsMenu(menu);
        }

        getMenuInflater().inflate(R.menu.main, menu);
        mMenu = menu;

        MenuItem ratioItem = menu.findItem(R.id.aspect_ratio);
        initMenuItem(ratioItem);

        MenuItem flashItem = menu.findItem(R.id.switch_flash);
        initMenuItem(flashItem);
        List<Integer> flashList = cameraView.getSupportedFlashModes();
        flashItem.setEnabled(!flashList.isEmpty());
        updateFlashMenu(flashItem, cameraView.getFlashMode());

        MenuItem cameraItem = menu.findItem(R.id.switch_camera);
        initMenuItem(cameraItem);
        cameraItem.setEnabled(cameraView.getNumberOfCameras() > 1);

        return true;
    }

    private void initMenuItem(final MenuItem item) {
        View view = item.getActionView();
        if (view == null) {
            return;
        }
        view.setBackgroundDrawable(item.getIcon());

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOptionsItemSelected(item);
            }
        });
    }

    private void updateFlashMenu(MenuItem item, @Flash int flashMod) {
        if (item == null || item.getActionView() == null) {
            return;
        }

        int resId = R.drawable.ic_flash_off;
        switch (flashMod) {
            case Constants.FLASH_OFF:
                resId = R.drawable.ic_flash_off;
                break;

            case Constants.FLASH_ON:
                resId = R.drawable.ic_flash_on;
                break;

            case Constants.FLASH_TORCH:
                resId = R.drawable.ic_flash_auto;
                break;

            case Constants.FLASH_AUTO:
                resId = R.drawable.ic_flash_auto;
                break;

            case Constants.FLASH_RED_EYE:
                resId = R.drawable.ic_flash_auto;
                break;
        }

        item.getActionView().setBackgroundResource(resId);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.aspect_ratio:
                item.getMenuInfo();
                showRatioPop(item.getActionView());
                return true;

            case R.id.switch_flash:
                showFlashPop(item.getActionView());
                return true;

            case R.id.switch_camera:
                if (cameraOpened) {
                    cameraOpened = false;
                    if (cameraView.getFacing() == Constants.FACING_FRONT) {
                        cameraView.openCamera(Constants.FACING_BACK);
                    } else {
                        cameraView.openCamera(Constants.FACING_FRONT);
                    }
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackKeyClicked();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void onBackKeyClicked() {
        finish();
        overridePendingTransition(0, R.anim.activity_out_to_bottom);
    }

    //显示选择闪光灯对话框
    private void showFlashPop(View anchorView) {
        int currentFlash = cameraView.getFlashMode();
        List<Integer> flashModes = cameraView.getSupportedFlashModes();
        final List<ItemEntity> list = new ArrayList<>();

        for (int i = 0; i < flashModes.size(); i++) {
            int flash = flashModes.get(i);
            ItemEntity item = new ItemEntity();
            item.setValue(flash);
            item.setSelected(flash == currentFlash);

            if (flash == Constants.FLASH_OFF) {
                item.setKey("off");
                list.add(item);

            } else if (flash == Constants.FLASH_ON) {
                item.setKey("on");
                list.add(item);

            } else if (flash == Constants.FLASH_AUTO) {
                item.setKey("auto");
                list.add(item);
            }
        }

        if (list.isEmpty()) {
            return;
        }

        final ListPopupWindow pop = new ListPopupWindow(this);
        pop.setAdapter(new MyAdapter(mActivity, list));
        pop.setWidth(Utils.dip2px(mActivity, 78));
        pop.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        pop.setAnchorView(anchorView);//设置ListPopupWindow的锚点，即关联PopupWindow的显示位置和这个锚点
        pop.setModal(true);//设置是否是模式

        pop.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int flash = (int) list.get(position).getValue();
                if (cameraView.setFlashMode(flash) && mMenu != null) {
                    MenuItem item = mMenu.findItem(R.id.switch_flash);
                    updateFlashMenu(item, flash);
                    saveStatus.flash = flash;
                }
                pop.dismiss();
            }
        });

        pop.show();
    }

    //显示选择比例对话框
    private void showRatioPop(View anchorView) {
        AspectRatio currentRatio = cameraView.getAspectRatio();
        Set<AspectRatio> ratioModes = cameraView.getSupportedAspectRatios();
        final List<ItemEntity> list = new ArrayList<>();

        for (AspectRatio ratio : ratioModes) {
            ItemEntity item = new ItemEntity();
            item.setKey(ratio.toString());
            item.setValue(ratio);
            item.setSelected(ratio.equals(currentRatio));
            list.add(item);
        }

        if (list.isEmpty()) {
            return;
        }

        final ListPopupWindow pop = new ListPopupWindow(this);
        pop.setAdapter(new MyAdapter(mActivity, list));
        pop.setWidth(Utils.dip2px(mActivity, 78));
        pop.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        pop.setAnchorView(anchorView);//设置ListPopupWindow的锚点，即关联PopupWindow的显示位置和这个锚点
        pop.setModal(true);//设置是否是模式

        pop.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AspectRatio ratio = (AspectRatio) list.get(position).getValue();
                if (cameraView.setAspectRatio(ratio) && mMenu != null) {
                    //MenuItem item = mMenu.findItem(R.id.aspect_ratio);
                    //updateFlashMenu(item, flash);
                    saveStatus.ratio = ratio;
                }
                pop.dismiss();
            }
        });

        pop.show();
    }

    @Override
    public void takePhoto() {
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "picture.jpg");
        cameraView.takePhoto(file);
    }

    @Override
    public void startRecord() {
        videoFile = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "video.mp4");
        cameraView.startRecord(videoFile);
    }

    @Override
    public void stopRecord() {
        cameraView.stopRecord();
        if (videoFile != null) {
            String msg = "图片已保存至:" + videoFile.getAbsolutePath();
            Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
        }
    }
}
