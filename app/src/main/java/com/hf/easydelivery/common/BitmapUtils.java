package com.hf.easydelivery.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;

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
        int quality = 90;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);

        // 多次降低质量直至小于 maxBytes
        while (out.toByteArray().length > maxBytes && quality > 30) {
            out.reset();
            quality -= 10;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        }

        // 若还太大，则缩小分辨率再压缩
        while (out.toByteArray().length > maxBytes && bitmap.getWidth() > 320 && bitmap.getHeight() > 320) {
            // 缩小为原来 80%
            int newWidth = (int)(bitmap.getWidth() * 0.8);
            int newHeight = (int)(bitmap.getHeight() * 0.8);
            bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            out.reset();
            quality = 80;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        }

        byte[] bytes = out.toByteArray();
        Bitmap result = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (result != null) return result;
        return bitmap; // 如果失败，返回原图
    }
}
