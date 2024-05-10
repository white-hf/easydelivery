package com.uniuni.SysMgrTool.View;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.uniuni.SysMgrTool.R;

import java.io.File;

public class FullImageFragment extends Fragment {

    private ImageView imageView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_full_image, container, false);

        RelativeLayout rootLayout = rootView.findViewById(R.id.root_layout);
        // 设置点击事件监听
        rootLayout.setOnClickListener(v->getParentFragmentManager().popBackStack());


        imageView = rootView.findViewById(R.id.full_image_view);
        // 获取传递过来的图片文件路径
        String imagePath = getArguments().getString("imageFile");
        // 加载图片并显示在 ImageView 中
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        imageView.setImageBitmap(bitmap);
        return rootView;
    }
}

