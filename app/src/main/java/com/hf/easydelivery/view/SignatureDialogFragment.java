package com.hf.easydelivery.view;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.hf.easydelivery.R;
import com.hf.courierservice.apihelper.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SignatureDialogFragment extends DialogFragment {

    private static final String ARG_RECIPIENT_NAME = "recipient_name";
    private SignatureView signatureView;
    private EditText etRecipientName;
    private OnSignatureCompletedListener listener;

    public interface OnSignatureCompletedListener {
        void onSignatureCompleted(String signaturePath, String recipientName);
    }

    public static SignatureDialogFragment newInstance(String recipientName) {
        SignatureDialogFragment fragment = new SignatureDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RECIPIENT_NAME, recipientName);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnSignatureCompletedListener(OnSignatureCompletedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_signature_dialog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        signatureView = view.findViewById(R.id.signature_view);
        etRecipientName = view.findViewById(R.id.et_recipient_name);
        Button btnClear = view.findViewById(R.id.btn_clear);
        Button btnComplete = view.findViewById(R.id.btn_complete);

        if (getArguments() != null) {
            String name = getArguments().getString(ARG_RECIPIENT_NAME);
            etRecipientName.setText(name);
        }

        btnClear.setOnClickListener(v -> signatureView.clear());

        btnComplete.setOnClickListener(v -> {
            Bitmap bitmap = signatureView.getSignatureBitmap();
            if (bitmap != null) {
                String path = saveBitmap(bitmap);
                if (path != null && listener != null) {
                    listener.onSignatureCompleted(path, etRecipientName.getText().toString());
                    dismiss();
                } else {
                    Toast.makeText(getContext(), "Failed to save signature", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String saveBitmap(Bitmap bitmap) {
        try {
            File cacheDir = getContext().getExternalCacheDir();
            if (cacheDir == null)
                cacheDir = getContext().getCacheDir();
            File file = new File(cacheDir, "signature_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            return file.getAbsolutePath();
        } catch (IOException e) {
            FileLog.getInstance().error("SignatureDialog", "Save signature failed", e);
            return null;
        }
    }
}
