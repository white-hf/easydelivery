package com.uniuni.SysMgrTool.Response;
public class Operation {

    private long id;
    private long order_id;
    private String operation_type;
    private String description;
    private String from;
    private String to;
    private String operator;
    private long utc_add_time;
    private String tno;
    private String payload;
    private String memo;
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

    public void setOperation_type(String operation_type) {
        this.operation_type = operation_type;
    }
    public String getOperation_type() {
        return operation_type;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

    public void setFrom(String from) {
        this.from = from;
    }
    public String getFrom() {
        return from;
    }

    public void setTo(String to) {
        this.to = to;
    }
    public String getTo() {
        return to;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
    public String getOperator() {
        return operator;
    }

    public void setUtc_add_time(long utc_add_time) {
        this.utc_add_time = utc_add_time;
    }
    public long getUtc_add_time() {
        return utc_add_time;
    }

    public void setTno(String tno) {
        this.tno = tno;
    }
    public String getTno() {
        return tno;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
    public String getPayload() {
        return payload;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }
    public String getMemo() {
        return memo;
    }

}