package com.uniuni.SysMgrTool.View;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.R;
import com.uniuni.SysMgrTool.dao.DeliveryInfo;

public class SmsBottomSheetFragment extends BottomSheetDialogFragment {

    private static final int SMS_PERMISSION_REQUEST_CODE = 1;

    private EditText smsEditText;
    private Button sendButton;
    private ListView templateListView;
    private String lastSentMessage;

    private Long mOrderId;

    public SmsBottomSheetFragment(Long orderId) {
        this.mOrderId = orderId;
    }

    public void setOrderId(Long mOrderId) {
        this.mOrderId = mOrderId;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sms_bottom_sheet, container, false);
        smsEditText = view.findViewById(R.id.sms_edit_text);
        sendButton = view.findViewById(R.id.send_button);
        templateListView = view.findViewById(R.id.template_list_view);

        if (!TextUtils.isEmpty(lastSentMessage)) {
            smsEditText.setText(lastSentMessage);
        }

        sendButton.setOnClickListener(v -> {
            String message = smsEditText.getText().toString().trim();
            if (!TextUtils.isEmpty(message)) {
                sendSMS(message);

                lastSentMessage = message;
                dismiss();
            } else {
                Toast.makeText(getContext(), "Message cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        // Set up the template list view here
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.sms_tempalte, android.R.layout.simple_list_item_1);

        templateListView.setAdapter(spinnerAdapter);
        templateListView.setOnItemClickListener((parent, view1, position, id) -> {
            smsEditText.setText(spinnerAdapter.getItem(position).toString());
        });

        return view;
    }

    private void sendSMS(String msg) {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_REQUEST_CODE);
        } else {
            final DeliveryInfo deliveryInfo = MySingleton.getInstance().getDeliveryinfoMgr().get(mOrderId);
            if (deliveryInfo != null) {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(deliveryInfo.getPhone(), null, msg , null, null);
                Toast.makeText(getContext(), "SMS sent.", Toast.LENGTH_SHORT).show();
            }
        }
    }

}
