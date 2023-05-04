package com.uniuni.SysMgrTool.View;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;

import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.R;
import com.uniuni.SysMgrTool.Request.InsertOperationLogReq;
import com.uniuni.SysMgrTool.Request.UpdateShippingStatusReq;
import com.uniuni.SysMgrTool.Response.OrderDetailData;
import com.uniuni.SysMgrTool.ServerInterface;

public class OderDetailView extends DialogFragment {

    private String mResut;
    private DialogFragment myHandle;
    private final int choiceSelfPick = 0;
    private final int choicePuttingInStorage = 1;
    private final int choiceScanParcel = 2;

    public String getmMsg() {
        return mMsg;
    }

    public void setmMsg(String mMsg) {
        this.mMsg = mMsg;
    }

    private String mMsg;

    public void setmOrderDetailData(OrderDetailData mOrderDetailData) {
        this.mOrderDetailData = mOrderDetailData;
    }

    private OrderDetailData mOrderDetailData;
    private int mCheckedItem = choiceSelfPick;

    private ProgressDialog mProgressDialog;

    public OderDetailView(OrderDetailData d)
    {
        mOrderDetailData = d;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        myHandle = this;
        mProgressDialog = new ProgressDialog(getActivity());
        mProgressDialog.setMessage(getString(R.string.str_waiting_svr));
        mProgressDialog.setTitle(getString(R.string.str_operation));
        mProgressDialog.setCancelable(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final AlertDialog dialog = builder.create();

        View dialogView  =View.inflate(MySingleton.getInstance().getCtx() , R.layout.operationlayout , null);
        dialog.setView(dialogView);

        TextView tvOrderDetail = dialogView.findViewById(R.id.orderDetailInfo);
        tvOrderDetail.setText(mMsg);
        tvOrderDetail.setTextIsSelectable(true);

        Button btnClose = dialogView.findViewById(R.id.buttonClose);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myHandle.dismiss();
                dialog.dismiss();
            }
        });

        RadioGroup rg = dialogView.findViewById(R.id.operation_group);

        Button btnOperation = dialogView.findViewById(R.id.button_operation);
        btnOperation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int iCheck = rg.getCheckedRadioButtonId();
                if (iCheck == R.id.radioButtonSelfPick)
                {
                    handleSelfPick();
                    myHandle.dismiss();
                    dialog.dismiss();
                }
                else if (iCheck == R.id.radioBtnReset)
                {
                    handleReset();
                    myHandle.dismiss();
                    dialog.dismiss();
                }
                else if (iCheck == R.id.radioButtonPutinStorage)
                {
                    mProgressDialog.show();
                    // START THE GAME!
                    new Thread(new Runnable() {
                        @Override
                        public void run() {

                            String storageInfo = MySingleton.getInstance().getServerInterface().putinStorage(mOrderDetailData);
                            if (storageInfo != null)
                            {
                                mResut = storageInfo;
                            }
                            else
                                mResut = "入库失败";

                            myHandle.getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mProgressDialog.cancel();
                                    mProgressDialog.dismiss();

                                    Toast.makeText(MySingleton.getInstance().getCtx(),  mResut , Toast.LENGTH_LONG).show();

                                    myHandle.dismiss();
                                    dialog.dismiss();
                                }
                            });

                        }
                    }).start();

                }else if (iCheck == R.id.radioButtonScan)
                {
                    MySingleton.getInstance().getServerInterface().handleScanParcel(mOrderDetailData.getOrders().getOrder_id() , mOrderDetailData.getOrders().getOrder_sn() , true);
                    myHandle.dismiss();
                    dialog.dismiss();

                }else {
                    myHandle.dismiss();
                    dialog.dismiss();
                }
            }
        });

        // Create the AlertDialog object and return it
        return dialog;
    }

    private void handleReset()
    {
        MySingleton.getInstance().getServerInterface().setParcelState(mOrderDetailData.getOrders().getTno() , ServerInterface.ParcelStatus.IN_TRANSIT.getValue());
    }

    public void handleSelfPick()
    {
        UpdateShippingStatusReq req = new UpdateShippingStatusReq();
        req.setOrder_id(mOrderDetailData.getOrders().getOrder_id());
        req.setStaff_id(666);
        req.setShipping_status(2);
        req.setScan_location(getString(R.string.default_scanned_location));
        req.setStoraged_warehouse(getResources().getInteger(R.integer.default_warehouse_id));
        req.setSend_sms(0);
        req.setException("");

        req.getParcel_info().setOrder_id(req.getOrder_id());
        req.getParcel_info().setExtra_order_sn(mOrderDetailData.getOrders().getOrder_sn());
        req.getParcel_info().setTransition(getString(R.string.str_self_pick));
        req.getParcel_info().setStatus(getResources().getInteger(R.integer.status_self_pick));
        req.getParcel_info().setDesc("");
        req.getParcel_info().setWarehouse(getResources().getInteger(R.integer.default_warehouse_id));

        MySingleton.getInstance().getServerInterface().updatesShippingStatus(req , true);

        InsertOperationLogReq logReq = new InsertOperationLogReq();
        logReq.setOrder_id(mOrderDetailData.getOrders().getOrder_id());
        logReq.setOperator(MySingleton.getInstance().getLoginInfo().loginName);
        logReq.setOperation_code(getResources().getInteger(R.integer.status_self_pick));
        logReq.setOperation_type(getResources().getInteger(R.integer.default_operation_type));
        logReq.setDescription("");
        logReq.setMemo("");

        MySingleton.getInstance().getServerInterface().insertOperationLog(logReq);
    }


}
