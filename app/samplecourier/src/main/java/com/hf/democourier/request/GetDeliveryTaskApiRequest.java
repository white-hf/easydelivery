package com.hf.democourier.request;


import com.hf.courierservice.apihelper.ApiRequestBase;
import com.hf.democourier.CourierService;
import com.hf.democourier.response.GetDeliveringListRsp;

import java.util.HashMap;

public class GetDeliveryTaskApiRequest extends ApiRequestBase<NullReq, GetDeliveringListRsp> {

    private static final String URL_DELIVERING_LIST = "Your domain" + "delivery/list?driver_id=";


    public GetDeliveryTaskApiRequest(NullReq req , String driverId , boolean bDelivering) {
        super(req, GetDeliveringListRsp.class);

        if (!bDelivering)
            mUrl = "...."; //modify the url based on your API
        else
            mUrl = URL_DELIVERING_LIST;
        mUrl += driverId;

        //set your headers, including authorization token
        HashMap<String,String> headers = new HashMap<>();
        headers.put("authorization", "Bearer" + " " + CourierService.userToken);

        this.setHeader(headers);
    }
}
