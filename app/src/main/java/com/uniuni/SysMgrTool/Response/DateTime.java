package com.uniuni.SysMgrTool.Response;

public class DateTime {

    private String timezone;
    private String offsetByGMT;
    private String localTime;
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
    public String getTimezone() {
        return timezone;
    }

    public void setOffsetByGMT(String offsetByGMT) {
        this.offsetByGMT = offsetByGMT;
    }
    public String getOffsetByGMT() {
        return offsetByGMT;
    }

    public void setLocalTime(String localTime) {
        this.localTime = localTime;
    }
    public String getLocalTime() {
        return localTime;
    }

}