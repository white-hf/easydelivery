package com.hf.courierservice.apihelper;

import android.annotation.SuppressLint;
import android.util.Log;

import com.hf.courierservice.bean.DeliveredUploadParams;
import com.hf.courierservice.apihelper.FileLog;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MultipartUploader {

    private static final String TAG = "MultipartUploader";
    private static final String USER_AGENT = "DriverApp/1.25.3 Dalvik/2.1.0 (Linux; U; Android 15; LE2110 Build/BP1A.250505.005; OnePlus)";
    private static final String CLIENT_IDENTIFIER = "android/com.uniuni.driver/1.25.3";

    @SuppressLint("DefaultLocale")
    public static boolean upload(DeliveredUploadParams params, Callback callback) {
        OkHttpClient client = new OkHttpClient();

        final String boundary = UUID.randomUUID().toString();
        final MultipartRequestBody requestBody = buildRequestBodyInternal(params, boundary);

        Request request = new Request.Builder()
                .url(params.getUrl())
                .post(requestBody)
                .header("Authorization", params.getAuthorization() == null ? "" : params.getAuthorization())
                .header("Accept-Language", "en")
                .header("User-Agent", USER_AGENT)
                .header("Client-Identifier", CLIENT_IDENTIFIER)
                .header("Accept-Encoding", "gzip")
                .header("Connection", "Keep-Alive")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .build();

        Log.d(TAG, "Request : " + request);
        logMultipartPreview(params, boundary);
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

    public static RequestBody buildRequestBodyForTest(DeliveredUploadParams params, String boundary) {
        return buildRequestBodyInternal(params, boundary == null ? UUID.randomUUID().toString() : boundary);
    }

    private static MultipartRequestBody buildRequestBodyInternal(DeliveredUploadParams params, String boundary) {
        final List<Map.Entry<String, String>> leadingFields = orderedEntries(params.getFormFields());
        final List<Map.Entry<String, String>> trailingFields = orderedEntries(params.getTrailingFields());
        final List<ImagePart> imageFiles = parseImagePath(params.getImageFiles());
        final String trackingId = params.getTrackingId();

        if (imageFiles.isEmpty()) {
            Log.w(TAG, "upload: no valid image files parsed from imagePath");
        }

        return new MultipartRequestBody(boundary, leadingFields, imageFiles, trailingFields, trackingId);
    }

    private static void logMultipartPreview(DeliveredUploadParams params, String boundary) {

    }

    private static List<Map.Entry<String, String>> orderedEntries(Map<String, String> source) {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return entries;
        }
        if (source instanceof LinkedHashMap) {
            entries.addAll(source.entrySet());
        } else {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                entries.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            }
        }
        return entries;
    }

    private static List<ImagePart> parseImagePath(String imagePath) {
        List<ImagePart> fileList = new ArrayList<>();

        try {
            if (imagePath == null) {
                Log.w(TAG, "parseImagePath: imagePath is null");
                FileLog.getInstance().writeLog("[MultipartUploader] parseImagePath: imagePath is null");
                return fileList;
            }
            String trimmed = imagePath.trim();
            if (trimmed.isEmpty()) {
                Log.w(TAG, "parseImagePath: imagePath is empty");
                FileLog.getInstance().writeLog("[MultipartUploader] parseImagePath: imagePath is empty");
                return fileList;
            }

            String content = trimmed;
            if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() >= 2) {
                content = trimmed.substring(1, trimmed.length() - 1);
            }

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
                    FileLog.getInstance().writeLog("[MultipartUploader] parseImagePath: file not found -> " + resolvedPath);
                    continue;
                }
                if (!f.isFile()) {
                    Log.w(TAG, "parseImagePath: not a file -> " + resolvedPath);
                    FileLog.getInstance().writeLog("[MultipartUploader] parseImagePath: not a file -> " + resolvedPath);
                    continue;
                }
                if (f.length() <= 0) {
                    Log.w(TAG, "parseImagePath: zero length file -> " + resolvedPath);
                    FileLog.getInstance().writeLog("[MultipartUploader] parseImagePath: zero length file -> " + resolvedPath);
                    continue;
                }
                fileList.add(new ImagePart(f, originalLabel));
            }
        } catch (Throwable t) {
            Log.e(TAG, "parseImagePath: unexpected error: " + t.getMessage());
            FileLog.getInstance().writeLog("[MultipartUploader] parseImagePath error: " + t.getMessage());
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

        String fallbackFileName() {
            if (file != null) return file.getName();
            if (originalName != null && !originalName.isEmpty()) return originalName;
            return "image.jpg";
        }
    }

    private static final class MultipartRequestBody extends RequestBody {
        private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);
        private final String boundary;
        private final byte[] boundaryPrefix;
        private final byte[] closingBoundary;
        private final List<Map.Entry<String, String>> leadingFields;
        private final List<ImagePart> imageParts;
        private final List<Map.Entry<String, String>> trailingFields;
        private final String trackingId;

        MultipartRequestBody(String boundary,
                             List<Map.Entry<String, String>> leadingFields,
                             List<ImagePart> imageParts,
                             List<Map.Entry<String, String>> trailingFields,
                             String trackingId) {
            this.boundary = boundary;
            this.boundaryPrefix = ("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8);
            this.closingBoundary = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            this.leadingFields = leadingFields;
            this.imageParts = imageParts;
            this.trailingFields = trailingFields;
            this.trackingId = trackingId;
        }

        @Override
        public MediaType contentType() {
            return MediaType.parse("multipart/form-data; boundary=" + boundary);
        }

        @Override
        public long contentLength() {
            try {
                return computeLength();
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            writeFields(sink, leadingFields);
            writeFiles(sink, imageParts);
            writeFields(sink, trailingFields);
            sink.write(closingBoundary);
        }

        private void writeFields(BufferedSink sink, List<Map.Entry<String, String>> fields) throws IOException {
            if (fields == null) return;
            for (Map.Entry<String, String> entry : fields) {
                String key = entry.getKey();
                String value = entry.getValue() == null ? "" : entry.getValue();
                byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
                sink.write(boundaryPrefix);
                sink.writeUtf8("Content-Disposition: form-data; name=\"" + key + "\"\r\n");
                sink.writeUtf8("Content-Length: " + valueBytes.length + "\r\n\r\n");
                sink.write(valueBytes);
                sink.write(CRLF);
            }
        }

        private void writeFiles(BufferedSink sink, List<ImagePart> files) throws IOException {
            if (files == null) return;
            for (ImagePart part : files) {
                File file = part.file;
                if (file == null || !file.exists() || !file.isFile() || file.length() <= 0) {
                    continue;
                }
                sink.write(boundaryPrefix);
                sink.writeUtf8("Content-Disposition: form-data; name=\"pod_images[]\"; filename=\"" + buildUploadFileName(part) + "\"\r\n");
                sink.writeUtf8("Content-Type: image/jpg\r\n");
                sink.writeUtf8("Content-Length: " + file.length() + "\r\n\r\n");
                try (Source source = Okio.source(file)) {
                    sink.writeAll(source);
                }
                sink.write(CRLF);
            }
        }

        private long computeLength() throws IOException {
            long total = 0;
            total += lengthOfFields(leadingFields);
            total += lengthOfFiles(imageParts);
            total += lengthOfFields(trailingFields);
            total += closingBoundary.length;
            return total;
        }

        private long lengthOfFields(List<Map.Entry<String, String>> fields) {
            if (fields == null) return 0;
            long total = 0;
            for (Map.Entry<String, String> entry : fields) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
                String header = "Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n" +
                        "Content-Length: " + valueBytes.length + "\r\n\r\n";
                total += boundaryPrefix.length;
                total += header.getBytes(StandardCharsets.UTF_8).length;
                total += valueBytes.length;
                total += CRLF.length;
            }
            return total;
        }

        private long lengthOfFiles(List<ImagePart> files) {
            if (files == null) return 0;
            long total = 0;
            for (ImagePart part : files) {
                File file = part.file;
                if (file == null || !file.exists() || !file.isFile() || file.length() <= 0) {
                    continue;
                }
                String header = "Content-Disposition: form-data; name=\"pod_images[]\"; filename=\"" + buildUploadFileName(part) + "\"\r\n" +
                        "Content-Type: image/jpg\r\n" +
                        "Content-Length: " + file.length() + "\r\n\r\n";
                total += boundaryPrefix.length;
                total += header.getBytes(StandardCharsets.UTF_8).length;
                total += file.length();
                total += CRLF.length;
            }
            return total;
        }

        private String buildUploadFileName(ImagePart part) {
            final String expectedPrefix = "file:///data/user/0/com.uniuni.driver/files/failed/";
            String original = part.originalName;
            if (original != null && original.startsWith(expectedPrefix)) {
                return original;
            }
            String folder;
            if (trackingId != null && !trackingId.isEmpty()) {
                folder = trackingId;
            } else if (part.file != null && part.file.getParentFile() != null) {
                folder = part.file.getParentFile().getName();
            } else {
                folder = "default";
            }
            String fileName = part.file != null ? part.file.getName() : part.fallbackFileName();
            return expectedPrefix + folder + "/" + fileName;
        }
    }
}
