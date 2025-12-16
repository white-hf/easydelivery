package com.hf.easydelivery.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.hf.easydelivery.R;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.dao.DeliveryInfo;

public class SmsBottomSheetFragment extends BottomSheetDialogFragment {

    private EditText smsEditText;
    private Button sendButton;
    private ListView templateListView;
    private String lastSentMessage;
    private Long mOrderId;
    private DeliveryInfo currentDelivery;
    private String[] formattedTemplates = new String[0];
    // Fallback data when orderId lookup fails
    private String fallbackOrderSn;
    private String fallbackPhone;
    private String fallbackAddress;
    private String fallbackName;
    private String fallbackRoute;

    private static final String ARG_ORDER_ID = "arg_order_id";
    private static final String ARG_ORDER_SN = "arg_order_sn";
    private static final String ARG_PHONE = "arg_phone";
    private static final String ARG_ADDRESS = "arg_address";
    private static final String ARG_NAME = "arg_name";
    private static final String ARG_ROUTE = "arg_route";
    // 必须保留无参构造，系统恢复 Fragment 时需要
    public SmsBottomSheetFragment() {
    }

    public static SmsBottomSheetFragment newInstance(Long orderId) {
        SmsBottomSheetFragment f = new SmsBottomSheetFragment();
        Bundle args = new Bundle();
        if (orderId != null) args.putLong(ARG_ORDER_ID, orderId);
        f.setArguments(args);
        return f;
    }

    public static SmsBottomSheetFragment newInstance(Long orderId,
                                                     @Nullable String orderSn,
                                                     @Nullable String phone,
                                                     @Nullable String address,
                                                     @Nullable String name,
                                                     @Nullable String route) {
        SmsBottomSheetFragment f = new SmsBottomSheetFragment();
        Bundle args = new Bundle();
        if (orderId != null) args.putLong(ARG_ORDER_ID, orderId);
        if (orderSn != null) args.putString(ARG_ORDER_SN, orderSn);
        if (phone != null) args.putString(ARG_PHONE, phone);
        if (address != null) args.putString(ARG_ADDRESS, address);
        if (name != null) args.putString(ARG_NAME, name);
        if (route != null) args.putString(ARG_ROUTE, route);
        f.setArguments(args);
        return f;
    }

    public void setOrderId(Long mOrderId) {
        this.mOrderId = mOrderId;
        Bundle args = getArguments();
        if (args != null) {
            args.putLong(ARG_ORDER_ID, mOrderId != null ? mOrderId : -1L);
            setArguments(args);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null && (mOrderId == null || mOrderId <= 0)) {
            if (args.containsKey(ARG_ORDER_ID)) {
                mOrderId = args.getLong(ARG_ORDER_ID, -1L);
            }
            fallbackOrderSn = args.getString(ARG_ORDER_SN);
            fallbackPhone = args.getString(ARG_PHONE);
            fallbackAddress = args.getString(ARG_ADDRESS);
            fallbackName = args.getString(ARG_NAME);
            fallbackRoute = args.getString(ARG_ROUTE);
        }

        View view = inflater.inflate(R.layout.fragment_sms_bottom_sheet, container, false);
        smsEditText = view.findViewById(R.id.sms_edit_text);
        sendButton = view.findViewById(R.id.send_button);
        templateListView = view.findViewById(R.id.template_list_view);
        currentDelivery = (mOrderId != null && mOrderId > 0) ? ResourceMgr.getInstance().getDeliveryinfoMgr().get(mOrderId) : null;
        String phone = currentDelivery != null ? currentDelivery.getPhone() : fallbackPhone;
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(getContext(), "包裹信息缺失，无法发送短信", Toast.LENGTH_SHORT).show();
            dismissAllowingStateLoss();
            return view;
        }
        String[] rawTemplates = getResources().getStringArray(R.array.sms_tempalte);
        formattedTemplates = new String[rawTemplates.length];
        for (int i = 0; i < rawTemplates.length; i++) {
            formattedTemplates[i] = formatTemplate(rawTemplates[i]);
        }

        if (!TextUtils.isEmpty(lastSentMessage)) {
            smsEditText.setText(lastSentMessage);
        } else if (formattedTemplates.length > 0) {
            smsEditText.setText(formattedTemplates[0]);
        }

        sendButton.setOnClickListener(v -> {
            String message = smsEditText.getText().toString().trim();
            if (TextUtils.isEmpty(message)) {
                Toast.makeText(getContext(), "Message cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            attemptSendSMS(message);
        });

        // Set up the template list view here
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1,
                formattedTemplates);

        templateListView.setAdapter(adapter);
        templateListView.setOnItemClickListener((parent, view1, position, id) -> {
            if (position >= 0 && position < formattedTemplates.length) {
                smsEditText.setText(formattedTemplates[position]);
            }
        });

        return view;
    }

    private void attemptSendSMS(String msg) {
        launchSmsApp(msg);
    }

    private void launchSmsApp(String msg) {
        DeliveryInfo deliveryInfo = currentDelivery;
        String phone = deliveryInfo != null ? deliveryInfo.getPhone() : fallbackPhone;
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(getContext(), "收件人信息缺失，无法发送短信", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:" + phone));
            intent.putExtra("sms_body", msg);
            startActivity(intent);
            lastSentMessage = msg;
            dismiss();
        } catch (Exception ex) {
            Toast.makeText(getContext(), "无法打开短信应用: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatTemplate(String template) {
        if (template == null) return "";
        String tracking = currentDelivery != null ? safe(currentDelivery.getOrderSn()) : safe(fallbackOrderSn);
        String address = currentDelivery != null ? safe(currentDelivery.getAddress()) : safe(fallbackAddress);
        String name = currentDelivery != null ? safe(currentDelivery.getName()) : safe(fallbackName);
        String route = currentDelivery != null ? safe(currentDelivery.getRouteNumber()) : safe(fallbackRoute);
        return template
                .replace("{tracking}", tracking)
                .replace("{tracking_no}", tracking)
                .replace("{address}", address)
                .replace("{name}", name)
                .replace("{route}", route);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

}
