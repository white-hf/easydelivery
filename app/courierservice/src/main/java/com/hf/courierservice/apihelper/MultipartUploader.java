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

        // Add image files
        int i = 0;
        for (File file : imageFiles) {
            builder.addFormDataPart("pod_images[]", String.format("image%d.jpg", i++),
                    RequestBody.create(file, MEDIA_TYPE_JPEG));
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
            Response response = client.newCall(request).execute();
            callback.onResponse(null, response);

            return true;

        } catch (IOException e) {
            callback.onFailure(null, e);
            return false;
        }
    }

    // Convert string containing file paths to List<File>
    static private List<File> parseImagePath(String imagePath) {
        List<File> fileList = new ArrayList<>();

        // Check if the string is empty or doesn't have the expected format
        if (imagePath == null || imagePath.isEmpty()) {
            return fileList; // Return an empty list or handle error
        }

        // Remove leading and trailing square brackets and split the string
        String[] paths = imagePath.substring(1, imagePath.length() - 1).split(", ");

        // Iterate over paths, create File objects, and add them to the list
        for (String path : paths) {
            String trimmedPath = path.trim(); // Trim leading/trailing spaces
            fileList.add(new File(trimmedPath)); // Create File object and add to list
        }

        return fileList;
    }



}
