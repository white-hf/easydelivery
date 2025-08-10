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
import androidx.fragment.app.Fragment;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_full_image, container, false);

        ImageView imageView = rootView.findViewById(R.id.full_image_view);
        imageView.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        imageView.setOnLongClickListener(v -> deleteImage());

        imageFilePath = getArguments().getString("imageFile");
        imageIndex = getArguments().getInt("imageIndex");

        Bitmap bitmap = BitmapFactory.decodeFile(imageFilePath);
        imageView.setImageBitmap(bitmap);

        // 左上角关闭按钮
        View btnClose = rootView.findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> getParentFragmentManager().popBackStack());

        return rootView;
    }

    private boolean deleteImage() {
        if (imageFilePath != null) {
            FragmentActivity activity = getActivity();


            // Close the fragment
            Toast.makeText(getContext(), "Image deleted", Toast.LENGTH_SHORT).show();
            getParentFragmentManager().popBackStack();
            return true;
        }
        else
            return false;
    }
}

