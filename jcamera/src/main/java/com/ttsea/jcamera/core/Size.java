package com.ttsea.jcamera.core;

import java.io.Serializable;

import androidx.annotation.NonNull;

/**
 * 用来存储尺寸
 */
final class Size implements Comparable<Size>, Serializable, Cloneable {

    public final int width;
    public final int height;

    public Size(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Size size = (Size) o;

        if (width != size.width)
            return false;

        return height == size.height;
    }

    @Override
    public int hashCode() {
        // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
        return height ^ ((width << (Integer.SIZE / 2)) | (width >>> (Integer.SIZE / 2)));
    }

    @Override
    public int compareTo(@NonNull Size another) {
        return width * height - another.width * another.height;
    }
}
