package com.hf.courierservice.bean;

import java.util.Map;

/**
 * This class represents the parameters for the delivered packages upload request.
 */
public class DeliveredUploadParams {
    private String url;
    private String authorization;
    private Map<String, String> formFields;
    private Map<String, String> trailingFields;
    private String imageFiles;
    private String trackingId;

    private Long orderId;
    private Double longitude;
    private Double latitude;
    private String recipientName;
    private Integer deliveryResult;
    private Integer failedReason;


    // Getters and Setters
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }


    public Long getOrderId() {
        return orderId;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public Integer getDeliveryResult() {
        return deliveryResult;
    }

    public void setDeliveryResult(Integer deliveryResult) {
        this.deliveryResult = deliveryResult;
    }

    public Integer getFailedReason() {
        return failedReason;
    }

    public void setFailedReason(Integer failedReason) {
        this.failedReason = failedReason;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAuthorization() {
        return authorization;
    }

    public void setAuthorization(String authorization) {
        this.authorization = authorization;
    }

    public Map<String, String> getFormFields() {
        return formFields;
    }

    public void setFormFields(Map<String, String> formFields) {
        this.formFields = formFields;
    }

    public Map<String, String> getTrailingFields() {
        return trailingFields;
    }

    public void setTrailingFields(Map<String, String> trailingFields) {
        this.trailingFields = trailingFields;
    }

    public String getImageFiles() {
        return imageFiles;
    }

    public void setImageFiles(String imageFiles) {
        this.imageFiles = imageFiles;
    }

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }
}
