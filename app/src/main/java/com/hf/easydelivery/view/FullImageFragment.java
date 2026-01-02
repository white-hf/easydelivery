package com.hf.easydelivery.view;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.hf.easydelivery.R;

public class FullImageFragment extends DialogFragment {

    private String imageFilePath;
    private int imageIndex;

    public static FullImageFragment newInstance(String imagePath, int index) {
        FullImageFragment fragment = new FullImageFragment();
        Bundle args = new Bundle();
        args.putString("image_path", imagePath);
        args.putInt("index", index);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.black);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_full_image, container, false);

        ImageView imageView = rootView.findViewById(R.id.full_image_view);
        imageView.setOnClickListener(v -> dismiss());
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        Bundle args = getArguments();
        if (args != null) {
            imageFilePath = args.getString("image_path");
            imageIndex = args.getInt("index", -1);
        }

        if (imageFilePath != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(imageFilePath);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                Toast.makeText(getContext(), getString(R.string.full_image_load_failed_format, imageFilePath), Toast.LENGTH_SHORT).show();
                dismiss();
            }
        } else {
            Toast.makeText(getContext(), R.string.full_image_invalid_path, Toast.LENGTH_SHORT).show();
            dismiss();
        }

        View btnClose = rootView.findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> dismiss());

        return rootView;
    }
}
