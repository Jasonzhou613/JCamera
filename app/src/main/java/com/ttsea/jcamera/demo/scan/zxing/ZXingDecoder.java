package com.ttsea.jcamera.demo.scan.zxing;

import android.graphics.Rect;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.ttsea.jcamera.demo.debug.JLog;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ZXingDecoder {
    private final List<BarcodeFormat> formats = new ArrayList<>();
    private MultiFormatReader mMultiFormatReader;
    private static ZXingDecoder instance;

    private static final List<BarcodeFormat> ALL_FORMATS = new ArrayList<>();

    static {
        ALL_FORMATS.add(BarcodeFormat.AZTEC);
        ALL_FORMATS.add(BarcodeFormat.CODABAR);
        ALL_FORMATS.add(BarcodeFormat.CODE_39);
        ALL_FORMATS.add(BarcodeFormat.CODE_93);
        ALL_FORMATS.add(BarcodeFormat.CODE_128);
        ALL_FORMATS.add(BarcodeFormat.DATA_MATRIX);
        ALL_FORMATS.add(BarcodeFormat.EAN_8);
        ALL_FORMATS.add(BarcodeFormat.EAN_13);
        ALL_FORMATS.add(BarcodeFormat.ITF);
        ALL_FORMATS.add(BarcodeFormat.MAXICODE);
        ALL_FORMATS.add(BarcodeFormat.PDF_417);
        ALL_FORMATS.add(BarcodeFormat.QR_CODE);
        ALL_FORMATS.add(BarcodeFormat.RSS_14);
        ALL_FORMATS.add(BarcodeFormat.RSS_EXPANDED);
        ALL_FORMATS.add(BarcodeFormat.UPC_A);
        ALL_FORMATS.add(BarcodeFormat.UPC_E);
        ALL_FORMATS.add(BarcodeFormat.UPC_EAN_EXTENSION);
    }

    public static ZXingDecoder getInstance() {
        if (instance == null) {
            synchronized (ZXingDecoder.class) {
                if (instance == null) {
                    instance = new ZXingDecoder();
                }
            }
        }
        return instance;
    }

    private ZXingDecoder() {
        init();
    }

    private void init() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, getFormats());
        mMultiFormatReader = new MultiFormatReader();
        mMultiFormatReader.setHints(hints);
    }

    /**
     * 解码数据
     *
     * @param data   待解码的byte数据
     * @param rect   待解码范围
     * @param width  data数据宽度
     * @param height 待解码数据高度
     * @return 解码结果，为null的时候表示为解码成功
     */
    public Result decodeData(byte[] data, Rect rect, int width, int height) {
        if (rect == null || rect.isEmpty()
                || width <= 0 || height <= 0) {
            return null;
        }

        if (rect.width() > width) {
            rect.left = rect.left * width / rect.width();
            rect.right = rect.right * width / rect.width();
        }

        if (rect.height() > height) {
            rect.top = rect.top * height / rect.height();
            rect.bottom = rect.bottom * height / rect.height();
        }

        try {
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, width, height,
                    rect.left, rect.top, rect.width(), rect.height(), false);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = mMultiFormatReader.decodeWithState(bitmap);

            if (result == null) {
                LuminanceSource invertedSource = source.invert();
                bitmap = new BinaryBitmap(new HybridBinarizer(invertedSource));
                result = mMultiFormatReader.decodeWithState(bitmap);
            }

            return result;

        } catch (Exception e) {
            JLog.w("Exception e:" + e.getMessage());
            // e.printStackTrace();

        } finally {
            mMultiFormatReader.reset();
        }

        return null;
    }

    public List<BarcodeFormat> getFormats() {
        if (formats.isEmpty()) {
            formats.addAll(ALL_FORMATS);
        }
        return formats;
    }

    /**
     * 设置解码格式
     *
     * @param formats
     */
    public void setFormats(List<BarcodeFormat> formats) {
        this.formats.clear();
        if (formats != null) {
            this.formats.addAll(formats);
        }
    }
}
