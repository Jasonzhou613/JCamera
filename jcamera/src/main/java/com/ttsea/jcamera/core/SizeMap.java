package com.ttsea.jcamera.core;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

final class SizeMap {

    /** 以比例存储对应的Size，同比例可能会有多个不同的Size */
    private final ArrayMap<AspectRatio, SortedSet<Size>> mRatios = new ArrayMap<>();

    void addSize(Size size) {
        AspectRatio ratio = AspectRatio.of(size.width, size.height);
        SortedSet<Size> sizes = mRatios.get(ratio);

        if (sizes == null) {
            sizes = new TreeSet<>();
            sizes.add(size);
            mRatios.put(ratio, sizes);

        } else {
            if (!sizes.contains(size)) {
                sizes.add(size);
            }
        }
    }

    void addAll(SizeMap sizeMap) {
        if (sizeMap == null || sizeMap.isEmpty()) {
            return;
        }

        for (AspectRatio ratio : sizeMap.keySet()) {
            SortedSet<Size> sizes = sizeMap.get(ratio);
            for (Size size : sizes) {
                addSize(size);
            }
        }
    }

    void remove(AspectRatio ratio) {
        mRatios.remove(ratio);
    }

    SortedSet<Size> get(AspectRatio ratio) {
        return mRatios.get(ratio);
    }

    Set<AspectRatio> keySet() {
        return mRatios.keySet();
    }

    void clear() {
        mRatios.clear();
    }

    boolean isEmpty() {
        return mRatios.isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        return mRatios.toString();
    }
}
