package com.uniuni.SysMgrTool.Response;

public class Path {

    private long id;
    private long order_id;
    private int code;
    private String pathAddr;
    private String pathInfo;
    private long pathTime;
    private int traceSeq;
    private String staff_id;
    private String scan_lat;
    private String scan_lng;
    private int is_updated;
    private String operate_warehouse;
    private int exception;
    private DateTime dateTime;
    public void setId(long id) {
        this.id = id;
    }
    public long getId() {
        return id;
    }

    public void setOrder_id(long order_id) {
        this.order_id = order_id;
    }
    public long getOrder_id() {
        return order_id;
    }

    public void setCode(int code) {
        this.code = code;
    }
    public int getCode() {
        return code;
    }

    public void setPathAddr(String pathAddr) {
        this.pathAddr = pathAddr;
    }
    public String getPathAddr() {
        return pathAddr;
    }

    public void setPathInfo(String pathInfo) {
        this.pathInfo = pathInfo;
    }
    public String getPathInfo() {
        return pathInfo;
    }

    public void setPathTime(long pathTime) {
        this.pathTime = pathTime;
    }
    public long getPathTime() {
        return pathTime;
    }

    public void setTraceSeq(int traceSeq) {
        this.traceSeq = traceSeq;
    }
    public int getTraceSeq() {
        return traceSeq;
    }

    public void setStaff_id(String staff_id) {
        this.staff_id = staff_id;
    }
    public String getStaff_id() {
        return staff_id;
    }

    public void setScan_lat(String scan_lat) {
        this.scan_lat = scan_lat;
    }
    public String getScan_lat() {
        return scan_lat;
    }

    public void setScan_lng(String scan_lng) {
        this.scan_lng = scan_lng;
    }
    public String getScan_lng() {
        return scan_lng;
    }

    public void setIs_updated(int is_updated) {
        this.is_updated = is_updated;
    }
    public int getIs_updated() {
        return is_updated;
    }

    public void setOperate_warehouse(String operate_warehouse) {
        this.operate_warehouse = operate_warehouse;
    }
    public String getOperate_warehouse() {
        return operate_warehouse;
    }

    public void setException(int exception) {
        this.exception = exception;
    }
    public int getException() {
        return exception;
    }

    public void setDateTime(DateTime dateTime) {
        this.dateTime = dateTime;
    }
    public DateTime getDateTime() {
        return dateTime;
    }

}