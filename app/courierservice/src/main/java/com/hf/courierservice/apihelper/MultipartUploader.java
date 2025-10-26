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
import java.util.UUID;

public class MultipartUploader {

    private static final String TAG = "MultipartUploader";
    private static final MediaType MEDIA_TYPE_JPEG = MediaType.parse("image/jpg");
    private static final String USER_AGENT = "DriverApp/1.25.3 Dalvik/2.1.0 (Linux; U; Android 15; LE2110 Build/BP1A.250505.005; OnePlus)";
    private static final String CLIENT_IDENTIFIER = "android/com.uniuni.driver/1.25.3";

    @SuppressLint("DefaultLocale")
    public static boolean upload(DeliveredUploadParams params, Callback callback) {
        OkHttpClient client = new OkHttpClient();

        MultipartBody.Builder builder = new MultipartBody.Builder(UUID.randomUUID().toString())
                .setType(MultipartBody.FORM);

        // Add form fields from map
        if (params.getFormFields() != null) {
            for (Map.Entry<String, String> entry : params.getFormFields().entrySet()) {
                builder.addFormDataPart(entry.getKey(), entry.getValue());
            }
        }

        // List of image files
        final List<ImagePart> imageFiles = parseImagePath(params.getImageFiles());

        // Add image files (skip missing/unreadable safely)
        if (imageFiles.isEmpty()) {
            Log.w(TAG, "upload: no valid image files parsed from imagePath");
        }
        for (ImagePart part : imageFiles) {
            try {
                File file = part.file;
                if (file != null && file.exists() && file.isFile() && file.length() > 0) {
                    builder.addFormDataPart("pod_images[]", part.uploadFileName(),
                            RequestBody.create(file, MEDIA_TYPE_JPEG));
                } else {
                    Log.w(TAG, "upload: skip invalid image file -> " + (file == null ? "null" : file.getAbsolutePath()));
                }
            } catch (Throwable t) {
                Log.e(TAG, "upload: failed to add image file: " + t.getMessage());
            }
        }

        if (params.getTrailingFields() != null) {
            for (Map.Entry<String, String> entry : params.getTrailingFields().entrySet()) {
                builder.addFormDataPart(entry.getKey(), entry.getValue());
            }
        }

        MultipartBody requestBody = builder.build();

        Request request = new Request.Builder()
                .url(params.getUrl())
                .post(requestBody)
                .header("Authorization", params.getAuthorization() == null ? "" : params.getAuthorization())
                .header("Accept-Language", "en")
                .header("User-Agent", USER_AGENT)
                .header("Client-Identifier", CLIENT_IDENTIFIER)
                .header("Accept-Encoding", "gzip")
                .header("Connection", "Keep-Alive")
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
    static private List<ImagePart> parseImagePath(String imagePath) {
        List<ImagePart> fileList = new ArrayList<>();

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
                String originalLabel = p;
                String resolvedPath = p;
                if (resolvedPath.startsWith("file://")) {
                    resolvedPath = resolvedPath.substring("file://".length());
                }
                File f = new File(resolvedPath);
                if (!f.exists()) {
                    Log.w(TAG, "parseImagePath: file not found -> " + resolvedPath);
                    continue;
                }
                if (!f.isFile()) {
                    Log.w(TAG, "parseImagePath: not a file -> " + p);
                    continue;
                }
                if (f.length() <= 0) {
                    Log.w(TAG, "parseImagePath: zero length file -> " + resolvedPath);
                    continue;
                }
                fileList.add(new ImagePart(f, originalLabel));
            }
        } catch (Throwable t) {
            Log.e(TAG, "parseImagePath: unexpected error: " + t.getMessage());
        }

        return fileList;
    }

    private static final class ImagePart {
        final File file;
        final String originalName;

        ImagePart(File file, String originalName) {
            this.file = file;
            this.originalName = originalName;
        }

        String uploadFileName() {
            String candidate = originalName;
            if (candidate == null || candidate.isEmpty()) {
                candidate = file != null ? file.getName() : "image.jpg";
            }
            final String expectedPrefix = "file:///data/user/0/com.uniuni.driver/files/failed/";
            if (candidate.startsWith(expectedPrefix)) {
                return candidate;
            }
            String folder = "default";
            if (file != null && file.getParentFile() != null) {
                folder = file.getParentFile().getName();
            }
            return expectedPrefix + folder + "/" + (file != null ? file.getName() : candidate);
        }
    }



}
