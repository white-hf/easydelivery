package com.hf.easydelivery.api;

import com.hf.courierservice.apihelper.MultipartUploader;
import com.hf.courierservice.bean.DeliveredUploadParams;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.RequestBody;
import okio.Buffer;

public class MultipartUploaderTest {

    @Test
    public void dumpMultipartRequest() throws Exception {
        File temp = File.createTempFile("sample_image", ".jpg");
        try (FileOutputStream fos = new FileOutputStream(temp)) {
            fos.write("FAKE_IMAGE_DATA".getBytes(StandardCharsets.UTF_8));
        }

        DeliveredUploadParams params = new DeliveredUploadParams();
        params.setUrl("https://delivery-service-api.uniuni.ca/delivery");
        params.setAuthorization("Bearer SAMPLE_TOKEN");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("order_id", "123456789");
        form.put("longitude", "-63.12345");
        form.put("latitude", "44.67890");
        params.setFormFields(form);
        Map<String, String> trailing = new LinkedHashMap<>();
        trailing.put("delivery_result", "0");
        trailing.put("recipient_name", "John Doe");
        params.setTrailingFields(trailing);
        params.setImageFiles(temp.getAbsolutePath());
        params.setTrackingId("TRACK123456");

        String boundary = "TEST-BOUNDARY";
        RequestBody body = MultipartUploader.buildRequestBodyForTest(params, boundary);
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        String raw = buffer.readString(StandardCharsets.UTF_8);
        String cleaned = raw.replace("FAKE_IMAGE_DATA", "<IMAGE_BYTES>");

        System.out.println("\n----- MULTIPART REQUEST BEGIN -----\n");
        System.out.println("Content-Type: multipart/form-data; boundary=" + boundary);
        System.out.println(cleaned);
        System.out.println("----- MULTIPART REQUEST END -----\n");
    }
}
