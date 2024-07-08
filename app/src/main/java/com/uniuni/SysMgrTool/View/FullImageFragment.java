package com.uniuni.SysMgrTool.View;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.uniuni.SysMgrTool.R;

import java.io.File;

public class FullImageFragment extends Fragment {

    private String imageFilePath;
    private int imageIndex;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_full_image, container, false);

        ImageView imageView = rootView.findViewById(R.id.full_image_view);
        imageView.setOnClickListener(v->getParentFragmentManager().popBackStack());
        imageView.setOnLongClickListener(v->deleteImage());

        imageFilePath = getArguments().getString("imageFile");
        imageIndex = getArguments().getInt("imageIndex");

        Bitmap bitmap = BitmapFactory.decodeFile(imageFilePath);
        imageView.setImageBitmap(bitmap);
        
        return rootView;
    }

    private boolean deleteImage() {
        if (imageFilePath != null) {
            FragmentActivity activity = getActivity();

            if (activity instanceof MapActivity) {
                ((MapActivity) activity).removeThumbnail(imageIndex);
            }
            // Close the fragment
            Toast.makeText(getContext(), "Image deleted", Toast.LENGTH_SHORT).show();
            getParentFragmentManager().popBackStack();
            return true;
        }
        else
            return false;
    }
}

