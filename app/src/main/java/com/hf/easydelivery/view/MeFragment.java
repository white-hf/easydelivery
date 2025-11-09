package com.hf.easydelivery.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.hf.easydelivery.R;
import com.hf.courierservice.apihelper.FileLog;

public class MeFragment extends Fragment {

    public MeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_me, container, false);
        Button myWorkButton = root.findViewById(R.id.btn_my_work);
        myWorkButton.setOnClickListener(v -> {
            if (getContext() != null) {
                Intent intent = new Intent(getContext(), MyWorkActivity.class);
                startActivity(intent);
            }
        });
        Button shareLogBtn = root.findViewById(R.id.btn_share_log);
        shareLogBtn.setOnClickListener(v -> shareLogFile());
        return root;
    }

    private void shareLogFile() {
        if (getContext() == null) return;
        Uri logUri = FileLog.getInstance().getShareUri();
        if (logUri == null) {
            Toast.makeText(getContext(), R.string.share_log_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_STREAM, logUri);
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_log_button));
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_log_button)));
    }
}
