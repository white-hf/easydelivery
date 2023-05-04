package com.uniuni.SysMgrTool.Request;

public class TransferPackagesReq extends RequestBase {

    private String sub_batch;
    private String route_no;
    private String start;
    private String end;
    private String receiver;
    private String pickup_model = "group_pickup";

    public void setSub_batch(String sub_batch) {
        this.sub_batch = sub_batch;
    }

    public String getSub_batch() {
        return sub_batch;
    }

    public void setRoute_no(String route_no) {
        this.route_no = route_no;
    }

    public String getRoute_no() {
        return route_no;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getStart() {
        return start;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public String getEnd() {
        return end;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setPickup_model(String pickup_model) {
        this.pickup_model = pickup_model;
    }

    public String getPickup_model() {
        return pickup_model;
    }


}
