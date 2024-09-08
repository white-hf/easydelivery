package com.hf.courierservice;

import android.content.Context;

import com.hf.courierservice.bean.DeliveredUploadParams;
import com.hf.courierservice.bean.DeliveringListData;

import java.util.List;


/**
 * The interface is used to adapt different couriers' service. EasyDelivery App uses {@link ICourierService} to
 * carry out the actual delivery work, and it is designed to run without network.
 *
 * <p>If you need to support a new courier service, you can implement this interface, and
 * it only comprises four basic methods.</p>
 * The interface is designed for asynchronous call, so you also need to implement  {@link IResponseCallBack}.
 * @author jvtang
 * @since 2024-08-16
 */
public interface ICourierService {
    /**
     * Pass application context to courier service and initialize the courier service.
     * @param context
     */
    void init(Context context);

    /**
     *  Login courier service, but this app does not need a token from courier service.
     * @param username
     * @param password
     * @param callback
     */
    void login(String username, String password, IResponseCallBack<String> callback);

    /*
     * Get delivery package list from courier service.
     * @param userId
     * @param bReadyForDelivery if true, the list will only contain packages that are ready for delivery,
     *        otherwise the info is for preview or scanning. The list will be cached in local to reduce network traffic
     *        and to even make the app run without network. Therefore, there might be a temporary data inconsistency between
     *        the app and the courier service, but it is not a problem.
     * @param callback
     */
    void getPackageList(String userId , boolean bReadyForDelivery, IResponseCallBack<List<DeliveringListData>> callback);

    /**
     * Upload delivered packages to courier service. This interface should be idempotent, so App might call it multiple times
     * for the same package because of poor network condition.
     * @param uploadInfo
     * @param callback
     */
    boolean uploadDeliveredPackages(DeliveredUploadParams uploadInfo, IResponseCallBack<Void> callback);
}
