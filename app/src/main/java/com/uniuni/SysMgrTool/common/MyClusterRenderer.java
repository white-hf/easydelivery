package com.uniuni.SysMgrTool.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.google.android.gms.maps.model.MarkerOptions;
import com.uniuni.SysMgrTool.R;

public class MyClusterRenderer<T extends ClusterItem> extends DefaultClusterRenderer<T> {

    private final Context mContext;

    public MyClusterRenderer(Context context, GoogleMap map, ClusterManager<T> clusterManager) {
        super(context, map, clusterManager);
        mContext = context;
    }

    @Override
    protected void onBeforeClusterItemRendered(T item, MarkerOptions markerOptions) {
        markerOptions.icon(createCustomMarker(item.getTitle()));
    }

    private BitmapDescriptor createCustomMarker(String title) {
        Drawable background = ContextCompat.getDrawable(mContext, R.drawable.ic_marker_background);
        background.setBounds(0, 0, background.getIntrinsicWidth(), background.getIntrinsicHeight());

        Bitmap bitmap = Bitmap.createBitmap(background.getIntrinsicWidth(), background.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        background.draw(canvas);

        Paint textPaint = new Paint();
        textPaint.setColor(ContextCompat.getColor(mContext, android.R.color.white));
        textPaint.setTextSize(36);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        Rect bounds = new Rect();
        textPaint.getTextBounds(title, 0, title.length(), bounds);
        int x = background.getIntrinsicWidth() / 2;
        int y = (background.getIntrinsicHeight() + bounds.height()) / 2;

        canvas.drawText(title, x, y, textPaint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    @Override
    protected boolean shouldRenderAsCluster(Cluster<T> cluster) {
        return cluster.getSize() > 2;
    }
}
