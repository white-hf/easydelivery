package com.hf.easydelivery.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class BitmapUtils {

    // 从文件中加载缩略图
    public static Bitmap decodeSampledBitmapFromFile(String imagePath, int reqWidth, int reqHeight) {
        // 首先获取图片的宽度和高度，但不实际加载图片到内存中
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, options);

        // 计算采样率
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // 使用计算出的采样率加载缩略图
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(imagePath, options);
    }

    public static int getRotationDegrees(String imagePath) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            // Log the error or handle it
            return 0;
        }
    }

    // 计算采样率
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // 计算最大的采样率，使得宽度和高度都大于或等于所需的宽度和高度
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * 将 Bitmap 按目标大小（字节）压缩，输出 JPEG 格式
     * @param bitmap   原始 Bitmap
     * @param maxBytes 目标最大字节数（如 120*1024 表示 120KB）
     * @return 压缩后的 Bitmap（失败则返回原图）
     */
    public static Bitmap compressBitmapToTarget(Bitmap bitmap, int maxBytes) {
        if (bitmap == null) return null;
        Bitmap working = bitmap;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        float quality = 0.8f;
        int iteration = 0;
        compressWithQuality(working, quality, out);
        final int maxIterations = 6;

        while (out.size() > maxBytes && iteration < maxIterations) {
            out.reset();
            quality *= 0.7f;
            if (quality < 0.1f) quality = 0.1f;
            compressWithQuality(working, quality, out);
            iteration++;
        }

        if (out.size() > maxBytes) {
            Bitmap resized = resizeMaxDimension(working, 720);
            if (resized != null) {
                working = resized;
                out.reset();
                compressWithQuality(working, 0.6f, out);
            }
        }

        byte[] data = out.toByteArray();
        Bitmap result = BitmapFactory.decodeByteArray(data, 0, data.length);
        return result != null ? result : working;
    }

    private static void compressWithQuality(Bitmap bitmap, float qualityFraction, ByteArrayOutputStream out) {
        int q = Math.max(1, Math.min(100, (int) (qualityFraction * 100)));
        bitmap.compress(Bitmap.CompressFormat.JPEG, q, out);
    }

    private static Bitmap resizeMaxDimension(Bitmap source, int maxDimension) {
        if (source == null) return null;
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) return source;
        int max = Math.max(width, height);
        if (max <= maxDimension) return source;
        float scale = maxDimension / (float) max;
        int newWidth = Math.max(1, Math.round(width * scale));
        int newHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
    }
}
