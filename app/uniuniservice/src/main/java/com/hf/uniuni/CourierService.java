package com.hf.uniuni;

import android.content.Context;

import com.hf.courierservice.ICourierService;
import com.hf.courierservice.ResponseCallBack;
import com.hf.courierservice.apihelper.MultipartUploader;
import com.hf.courierservice.apihelper.exception.UnAuthorizedException;
import com.hf.courierservice.bean.DeliveredUploadParams;
import com.hf.courierservice.bean.DeliveringListData;
import com.hf.courierservice.apihelper.ApiRequestBase;
import com.hf.uniuni.Request.AppLoginReq;
import com.hf.uniuni.Request.CommonReq;
import com.hf.uniuni.Request.GetDeliveryTaskApiRequest;
import com.hf.uniuni.Request.UserLoginTaskApiRequest;
import com.hf.uniuni.Response.UploadCallback;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Callback;

public class CourierService implements ICourierService {
    public static final String DOMAIN_API    = "https://delivery-service-api.uniuni.ca/";
    //public static final String DOMAIN_API    = "http://192.168.2.23:8964/";
    public static String userToken         ;

    @Override
    public void init(Context context) {
        ApiRequestBase.initRequestQueue(context);
    }

    @Override
    public void login(@NotNull String username, @NotNull String password, ResponseCallBack<String> callback) {
        AppLoginReq req = new AppLoginReq();
        req.setPassword(password);
        req.setCredential_id(username);

        UserLoginTaskApiRequest api = new UserLoginTaskApiRequest(req);
        api.doApi(callback);
    }

    @Override
    public void getPackageList(String userId , boolean bDelivered,ResponseCallBack<List<DeliveringListData>> callback) {
        CommonReq  req = new CommonReq();
        GetDeliveryTaskApiRequest api = new GetDeliveryTaskApiRequest(req , userId, bDelivered);
        api.doApi(callback);
    }

    @Override
    public boolean uploadDeliveredPackages(DeliveredUploadParams uploadInfo, ResponseCallBack<Void> callback) {
        String DELIVERED_API = DOMAIN_API + "delivery";

        DeliveredUploadParams params = new DeliveredUploadParams();
        params.setUrl(DELIVERED_API);

        if (userToken == null) {
            callback.onFail(new UnAuthorizedException());
            return false;
        }

        params.setAuthorization("Bearer" + " " + userToken);

        Map<String, String> formFields = new HashMap<>();
        formFields.put("delivered_location", "1");
        formFields.put("order_id", String.valueOf(uploadInfo.getOrderId()));
        formFields.put("longitude", String.valueOf(uploadInfo.getLongitude()));
        formFields.put("latitude",String.valueOf(uploadInfo.getLatitude()));
        formFields.put("delivery_result", "0");
        params.setFormFields(formFields);

        params.setImageFiles(uploadInfo.getImageFiles());
        Callback cb = new UploadCallback(callback);
        return  MultipartUploader.upload(params,cb);
    }
}
