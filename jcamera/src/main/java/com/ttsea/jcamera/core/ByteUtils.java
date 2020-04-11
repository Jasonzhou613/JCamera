package com.ttsea.jcamera.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

final class ByteUtils {

    public static void saveData(byte[] data, File file) throws Exception {
        if (file.exists()) {
            file.delete();
        }

        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(data);
            os.close();

        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}
