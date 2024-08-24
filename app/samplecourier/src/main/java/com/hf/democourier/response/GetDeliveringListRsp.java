package com.hf.democourier.response;

import com.hf.courierservice.ResponseCallBack;
import com.hf.courierservice.Result;
import com.hf.courierservice.apihelper.TaskBase;
import com.hf.courierservice.bean.DeliveringListData;

import java.util.List;

/**
 * You should define this class based on your API.
 * @author jvtang
 * @since 2024-08-23
 */
public class GetDeliveringListRsp implements TaskBase<List<DeliveringListData>> {
    private String rspCode;
    private String rspMessage;
    private List<DeliveringListData> rspData;

    public String getRspCode() {
        return rspCode;
    }

    public void setRspCode(String rspCode) {
        this.rspCode = rspCode;
    }

    public String getRspMessage() {
        return rspMessage;
    }

    public void setRspMessage(String rspMessage) {
        this.rspMessage = rspMessage;
    }

    public List<DeliveringListData> getRspData() {
        return rspData;
    }

    public void setRspData(List<DeliveringListData> rspData) {
        this.rspData = rspData;
    }

    @Override
    public void doIt(ResponseCallBack<List<DeliveringListData>> cb) {
        List<DeliveringListData> lst = this.getRspData();

        //do something here

        //pass the result to App
        cb.onComplete(new Result.Success<List<DeliveringListData>>(lst));
    }
}