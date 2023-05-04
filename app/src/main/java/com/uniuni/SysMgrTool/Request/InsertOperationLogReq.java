package com.uniuni.SysMgrTool.Request;

public class InsertOperationLogReq extends RequestBase {
    private String operator;
    private Integer operation_code;

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Integer getOperation_code() {
        return operation_code;
    }

    public void setOperation_code(Integer operation_code) {
        this.operation_code = operation_code;
    }

    public Integer getOperation_type() {
        return operation_type;
    }

    public void setOperation_type(Integer operation_type) {
        this.operation_type = operation_type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    private Integer operation_type;
    private String description;
    private String memo;
}
