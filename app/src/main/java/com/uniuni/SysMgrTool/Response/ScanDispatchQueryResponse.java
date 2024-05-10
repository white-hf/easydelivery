package com.uniuni.SysMgrTool.Response;

import com.uniuni.SysMgrTool.bean.BizData;

import java.util.List;

public class ScanDispatchQueryResponse extends ResponseBase{

    private String biz_code;
    private String biz_message;
    private List<BizData> biz_data;
    public void setBiz_code(String biz_code) {
        this.biz_code = biz_code;
    }
    public String getBiz_code() {
        return biz_code;
    }

    public void setBiz_message(String biz_message) {
        this.biz_message = biz_message;
    }
    public String getBiz_message() {
        return biz_message;
    }

    public void setBiz_data(List<BizData> biz_data) {
        this.biz_data = biz_data;
    }
    public List<BizData> getBiz_data() {
        return biz_data;
    }

}