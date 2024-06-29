package com.uniuni.SysMgrTool.common;

import com.uniuni.SysMgrTool.Request.DeliveredUploadParams;

import okhttp3.*;
import java.io.File;
import java.util.Map;

public class MultipartUploader {

    private static final MediaType MEDIA_TYPE_JPEG = MediaType.parse("image/jpeg");
    private static final String BOUNDARY = "Boundary-0EDAE93A-4CA4-40CE-87BC-CFB10948E044";

    public static void upload(DeliveredUploadParams params, Callback callback) {
        OkHttpClient client = new OkHttpClient();

        MultipartBody.Builder builder = new MultipartBody.Builder(BOUNDARY)
                .setType(MultipartBody.FORM);

        // Add form fields from map
        for (Map.Entry<String, String> entry : params.getFormFields().entrySet()) {
            builder.addFormDataPart(entry.getKey(), entry.getValue());
        }

        // Add image files
        for (File file : params.getImageFiles()) {
            builder.addFormDataPart("pod_images[]", file.getName(),
                    RequestBody.create(file, MEDIA_TYPE_JPEG));
        }

        MultipartBody requestBody = builder.build();

        Request request = new Request.Builder()
                .url(params.getUrl())
                .post(requestBody)
                .header("Authorization", params.getAuthorization())
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("User-Agent", "Uniuni%20Driver/1 CFNetwork/1496.0.7 Darwin/23.5.0")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-CA,en-US;q=0.9,en;q=0.8")
                .header("Connection", "keep-alive")
                .header("Accept-Encoding", "gzip, deflate, br")
                .build();

        client.newCall(request).enqueue(callback);
    }
}
