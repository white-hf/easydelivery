package com.hf.courierservice.apihelper;

import android.annotation.SuppressLint;
import android.util.Log;

import com.hf.courierservice.bean.DeliveredUploadParams;

import okhttp3.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MultipartUploader {

    private static final String TAG = "MultipartUploader";
    private static final MediaType MEDIA_TYPE_JPEG = MediaType.parse("image/jpeg");
    private static final String BOUNDARY = "Boundary-0EDAE93A-4CA4-40CE-87BC-CFB10948E044";

    @SuppressLint("DefaultLocale")
    public static boolean upload(DeliveredUploadParams params, Callback callback) {
        OkHttpClient client = new OkHttpClient();

        MultipartBody.Builder builder = new MultipartBody.Builder(BOUNDARY)
                .setType(MultipartBody.FORM);

        // Add form fields from map
        for (Map.Entry<String, String> entry : params.getFormFields().entrySet()) {
            builder.addFormDataPart(entry.getKey(), entry.getValue());
        }

        // List of image files
        final List<File> imageFiles = parseImagePath(params.getImageFiles());

        // Add image files (skip missing/unreadable safely)
        int i = 0;
        if (imageFiles.isEmpty()) {
            Log.w(TAG, "upload: no valid image files parsed from imagePath");
        }
        for (File file : imageFiles) {
            try {
                if (file != null && file.exists() && file.isFile() && file.length() > 0) {
                    builder.addFormDataPart("pod_images[]", String.format("image%d.jpg", i++),
                            RequestBody.create(file, MEDIA_TYPE_JPEG));
                } else {
                    Log.w(TAG, "upload: skip invalid image file -> " + (file == null ? "null" : file.getAbsolutePath()));
                }
            } catch (Throwable t) {
                Log.e(TAG, "upload: failed to add image file: " + t.getMessage());
            }
        }

        MultipartBody requestBody = builder.build();

        Request request = new Request.Builder()
                .url(params.getUrl())
                .post(requestBody)
                .header("Authorization", params.getAuthorization())
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("User-Agent", "EasyDelivery%20Driver/1 CFNetwork/1496.0.7 Darwin/23.5.0")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-CA,en-US;q=0.9,en;q=0.8")
                .header("Connection", "keep-alive")
                .header("Accept-Encoding", "gzip, deflate, br")
                .build();

        Log.d(TAG, "Request : " + request.toString());
        try {
            try (Response response = client.newCall(request).execute()) {
                callback.onResponse(null, response);
                return true;
            }
        } catch (IOException e) {
            callback.onFailure(null, e);
            return false;
        }
    }

    // Convert string containing file paths to List<File>
    static private List<File> parseImagePath(String imagePath) {
        List<File> fileList = new ArrayList<>();

        try {
            if (imagePath == null) {
                Log.w(TAG, "parseImagePath: imagePath is null");
                return fileList;
            }
            String trimmed = imagePath.trim();
            if (trimmed.isEmpty()) {
                Log.w(TAG, "parseImagePath: imagePath is empty");
                return fileList;
            }

            // Accept formats:
            // 1) "[/path/a.jpg, /path/b.jpg]"
            // 2) "/path/a.jpg,/path/b.jpg"
            // 3) single path: "/path/a.jpg"
            String content = trimmed;
            if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() >= 2) {
                content = trimmed.substring(1, trimmed.length() - 1);
            }

            // Split by comma if present; otherwise treat as a single path
            String[] paths = content.contains(",") ? content.split(",") : new String[]{content};

            for (String raw : paths) {
                String p = raw.trim();
                if (p.isEmpty()) continue;
                File f = new File(p);
                if (!f.exists()) {
                    Log.w(TAG, "parseImagePath: file not found -> " + p);
                    continue;
                }
                if (!f.isFile()) {
                    Log.w(TAG, "parseImagePath: not a file -> " + p);
                    continue;
                }
                if (f.length() <= 0) {
                    Log.w(TAG, "parseImagePath: zero length file -> " + p);
                    continue;
                }
                fileList.add(f);
            }
        } catch (Throwable t) {
            Log.e(TAG, "parseImagePath: unexpected error: " + t.getMessage());
        }

        return fileList;
    }



}
