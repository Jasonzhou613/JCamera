package com.ttsea.jcamera.demo.camera.model;

import com.ttsea.jcamera.core.AspectRatio;

public class SaveStatus {
    public int facing;
    public int flash;
    public AspectRatio ratio;

    @Override
    public String toString() {
        return "SaveStatus{" +
                "facing=" + facing +
                ", flash=" + flash +
                ", ratio=" + ratio +
                '}';
    }
}
