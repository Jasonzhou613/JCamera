package com.ttsea.jcamera.demo.camera.model;

public class ItemEntity {

    private String key;
    private Object value;
    private boolean selected;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public String toString() {
        return "ItemEntity{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", selected=" + selected +
                '}';
    }
}
