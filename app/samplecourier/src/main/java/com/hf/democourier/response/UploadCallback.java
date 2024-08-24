package com.hf.democourier.response;
import androidx.annotation.NonNull;

import com.hf.courierservice.ResponseCallBack;
import com.hf.courierservice.apihelper.exception.ForbiddenException;
import com.hf.courierservice.apihelper.exception.TooMuchRequestException;
import com.hf.courierservice.apihelper.exception.UnAuthorizedException;

import java.io.IOException;
import java.net.HttpURLConnection;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * The response of uploading might be complicated, we have to notify app all kinds of errors.
 *
 * <p>You add your specific exceptions based on your API.</p>
 *
 * @author jvtang
 * @since 2024-08-23
 */
public class UploadCallback implements Callback {

    private static final int TOO_MUCH_REQUEST = 1; //modify based on your API;
    private final ResponseCallBack<Void> callback;
    public UploadCallback(ResponseCallBack<Void> callback)
    {
        this.callback = callback;
    }

    @Override
    public void onFailure(Call call, @NonNull IOException e) {
        callback.onFail(e);
    }

    @Override
    public void onResponse(Call call, @NonNull Response response) throws IOException {
        if (!response.isSuccessful()) {
            //This is a unusual situation or an error that server knows, it should not be checked.
            //We need handle it according to the http code
            if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                //need to login again
                callback.onFail(new UnAuthorizedException());
            }
            else if (response.code() == HttpURLConnection.HTTP_FORBIDDEN)
            {
                //It is a repeated request, we have handled it successfully before.
                //Notify app not to request again.
                callback.onFail(new ForbiddenException());
            }
            else if (response.code() == TOO_MUCH_REQUEST)
            {
                //too many requests, we need to wait a while.
                callback.onFail(new TooMuchRequestException());
            }
            else {
                callback.onFail(new Exception("Upload response is unsuccessful:" + response.code()));
            }
        } else {
            // Handle successful HTTP response
            if (response.body() != null) {
                String responseBody = response.body().string();

                try {
                        //...do something based on your response body, then
                        callback.onComplete(null);

                } catch (Exception e) {
                    //this situation is unusual, it should not be checked.
                    callback.onFail(new Exception("Upload response is unsuccessful because of exception:" + e.getMessage()));
                }

            } else {
                //this situation is unusual, it should not be checked.
                callback.onFail(new Exception("Upload unsuccessfully due to empty body:"));
            }
        }
    }
}