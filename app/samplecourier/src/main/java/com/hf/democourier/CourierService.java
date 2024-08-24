package com.hf.democourier;

import android.content.Context;

import com.hf.courierservice.ICourierService;
import com.hf.courierservice.ResponseCallBack;
import com.hf.courierservice.apihelper.ApiRequestBase;
import com.hf.courierservice.apihelper.MultipartUploader;
import com.hf.courierservice.apihelper.exception.UnAuthorizedException;
import com.hf.courierservice.bean.DeliveredUploadParams;
import com.hf.courierservice.bean.DeliveringListData;
import com.hf.democourier.request.AppLoginReq;
import com.hf.democourier.request.NullReq;
import com.hf.democourier.request.GetDeliveryTaskApiRequest;
import com.hf.democourier.request.UserLoginTaskApiRequest;
import com.hf.democourier.response.UploadCallback;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Callback;

/**
 * This is a showcase of CourierService. You should replace this with your own implementation.
 * The {@link ICourierService} only includes the basic APIs, you can add more if necessary.
 * <p>You can use whatever method to call your APIs, {@link ApiRequestBase} is just a demo</p>
 * @author jvtang
 * @since 2024-08-23
 */
public class CourierService implements ICourierService {
    public static String userToken         ;//After login, save user token here for future calling other APIs

    @Override
    public void init(Context context) {
        //Must initialize request queue before calling any API
        ApiRequestBase.initRequestQueue(context);

        //other initialization of this courier service
        //...
    }

    @Override
    public void login(@NotNull String username, @NotNull String password, ResponseCallBack<String> callback) {
        AppLoginReq req = new AppLoginReq();
        req.setPwd(password);
        req.setUser(username);

        UserLoginTaskApiRequest api = new UserLoginTaskApiRequest(req);
        api.doApi(callback);
    }

    @Override
    public void getPackageList(String userId , boolean bDelivered,ResponseCallBack<List<DeliveringListData>> callback) {
        NullReq req = new NullReq();
        GetDeliveryTaskApiRequest api = new GetDeliveryTaskApiRequest(req , userId, bDelivered);
        api.doApi(callback);
    }

    @Override
    public boolean uploadDeliveredPackages(DeliveredUploadParams uploadInfo, ResponseCallBack<Void> callback) {
        String DELIVERED_API = "Your api url" + "delivery";

        DeliveredUploadParams params = new DeliveredUploadParams();
        params.setUrl(DELIVERED_API);

        //The function might be called before login, so check userToken first
        //Like App restarts after crashed, the uploading thread starts to run, and
        //We have some package data waiting to be uploaded.

        //The code might be redundant, I think it's better to check the response
        if (userToken == null) {
            callback.onFail(new UnAuthorizedException());
            return false;
        }

        params.setAuthorization("Bearer" + " " + userToken);

        Map<String, String> formFields = new HashMap<>();
        formFields.put("location", "...");
        formFields.put("id", String.valueOf(uploadInfo.getOrderId()));
        formFields.put("longitude", String.valueOf(uploadInfo.getLongitude()));
        formFields.put("latitude",String.valueOf(uploadInfo.getLatitude()));
        formFields.put("result", "0");
        params.setFormFields(formFields);

        params.setImageFiles(uploadInfo.getImageFiles());
        Callback cb = new UploadCallback(callback);
        return  MultipartUploader.upload(params,cb);
    }
}
