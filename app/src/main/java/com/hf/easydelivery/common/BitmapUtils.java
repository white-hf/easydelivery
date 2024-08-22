package com.hf.easydelivery.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

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
}
