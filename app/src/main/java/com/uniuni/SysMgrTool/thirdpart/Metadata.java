package com.uniuni.SysMgrTool.thirdpart;

public class Metadata {
    private double latitude;
    private double longitude;
    private String geocode_precision;
    private String max_geocode_precision;
    private String address_format;
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    public double getLatitude() {
        return latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    public double getLongitude() {
        return longitude;
    }

    public void setGeocode_precision(String geocode_precision) {
        this.geocode_precision = geocode_precision;
    }
    public String getGeocode_precision() {
        return geocode_precision;
    }

    public void setMax_geocode_precision(String max_geocode_precision) {
        this.max_geocode_precision = max_geocode_precision;
    }
    public String getMax_geocode_precision() {
        return max_geocode_precision;
    }

    public void setAddress_format(String address_format) {
        this.address_format = address_format;
    }
    public String getAddress_format() {
        return address_format;
    }

}
