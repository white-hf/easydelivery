package com.hf.easydelivery.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.LruCache;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.google.android.gms.maps.model.MarkerOptions;
import com.hf.easydelivery.core.LargeParcelStore;
import com.hf.easydelivery.R;
import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.HashMap;
import java.util.Map;

public class MyClusterRenderer<T extends ClusterItem> extends DefaultClusterRenderer<T> {

    private final Context mContext;
    private float mZoomLevel = 15;
    private boolean clusteringEnabled = true;
    private MarkerStylePolicy markerStylePolicy = new DefaultMarkerStylePolicy();
    private MarkerColorTone markerColorTone = MarkerColorTone.DELIVERING;
    private DeliveryInfo currentPrimaryDelivery;
    private final Map<String, com.google.android.gms.maps.model.LatLng> spiderfyPositions = new HashMap<>();
    private final LruCache<String, BitmapDescriptor> markerIconCache = new LruCache<>(256);

    public void setZoomLevel(float mZoomLevel) {
        this.mZoomLevel = mZoomLevel;
    }

    public void setClusteringEnabled(boolean clusteringEnabled) {
        this.clusteringEnabled = clusteringEnabled;
    }

    public void setMarkerStylePolicy(MarkerStylePolicy markerStylePolicy) {
        this.markerStylePolicy = markerStylePolicy == null ? new DefaultMarkerStylePolicy() : markerStylePolicy;
    }

    public void setMarkerColorTone(MarkerColorTone markerColorTone) {
        this.markerColorTone = markerColorTone == null ? MarkerColorTone.DELIVERING : markerColorTone;
    }

    public void setCurrentPrimaryDelivery(DeliveryInfo currentPrimaryDelivery) {
        this.currentPrimaryDelivery = currentPrimaryDelivery;
    }

    public MyClusterRenderer(Context context, GoogleMap map, ClusterManager<T> clusterManager) {
        super(context, map, clusterManager);
        mContext = context;
    }

    public void setSpiderfyPositions(Map<String, com.google.android.gms.maps.model.LatLng> positions) {
        spiderfyPositions.clear();
        if (positions != null) {
            spiderfyPositions.putAll(positions);
        }
    }

    @Override
    protected void onBeforeClusterItemRendered(T item, MarkerOptions markerOptions) {
        if (item instanceof DeliveryInfo) {
            DeliveryInfo info = (DeliveryInfo) item;
            String key = info.getStableKey();
            com.google.android.gms.maps.model.LatLng override = spiderfyPositions.get(key);
            if (override != null) {
                markerOptions.position(override);
            }
            MarkerStyleDecision styleDecision = markerStylePolicy.styleFor(
                    info,
                    currentPrimaryDelivery,
                    LargeParcelStore.isLarge(mContext, info),
                    markerColorTone);
            markerOptions.icon(createCustomMarker(info.getRouteNumber(), styleDecision));
            markerOptions.zIndex(styleDecision.highlighted ? 3f : (styleDecision.largeParcel ? 2f : 0f));
            return;
        }
        markerOptions.icon(createFallbackMarker(item.getTitle()));
    }

    @Override
    protected void onClusterItemUpdated(T item, Marker marker) {
        super.onClusterItemUpdated(item, marker);
        if (item instanceof DeliveryInfo) {
            DeliveryInfo info = (DeliveryInfo) item;
            String key = info.getStableKey();
            com.google.android.gms.maps.model.LatLng override = spiderfyPositions.get(key);
            if (override != null) {
                marker.setPosition(override);
            }
            MarkerStyleDecision styleDecision = markerStylePolicy.styleFor(
                    info,
                    currentPrimaryDelivery,
                    LargeParcelStore.isLarge(mContext, info),
                    markerColorTone);
            marker.setIcon(createCustomMarker(info.getRouteNumber(), styleDecision));
            marker.setZIndex(styleDecision.highlighted ? 3f : (styleDecision.largeParcel ? 2f : 0f));
            return;
        }
        marker.setIcon(createFallbackMarker(item.getTitle()));
    }

    private BitmapDescriptor createFallbackMarker(String title) {
        MarkerStyleDecision decision = new MarkerStyleDecision(markerColorTone, false, false, false);
        return createCustomMarker(title, decision);
    }

    private BitmapDescriptor createCustomMarker(String title, MarkerStyleDecision styleDecision) {
        String cacheKey = buildMarkerCacheKey(title, styleDecision);
        BitmapDescriptor cached = markerIconCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        int width = styleDecision.compact ? dp(42) : dp(50);
        int height = styleDecision.compact ? dp(56) : dp(66);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float w = width;
        float h = height;
        float circleRadius = w * 0.40f;
        float circleCenterX = w / 2f;
        float circleCenterY = circleRadius + dp(3);
        float tipX = w / 2f;
        float tipY = h - dp(4);

        Path path = new Path();
        path.addCircle(circleCenterX, circleCenterY, circleRadius, Path.Direction.CW);
        path.moveTo(circleCenterX - circleRadius * 0.75f, circleCenterY + circleRadius * 0.55f);
        path.lineTo(tipX, tipY);
        path.lineTo(circleCenterX + circleRadius * 0.75f, circleCenterY + circleRadius * 0.55f);
        path.close();

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(resolveMarkerColor(styleDecision.colorTone));
        canvas.drawPath(path, fillPaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(styleDecision.highlighted ? dp(3) : dp(1));
        borderPaint.setColor(styleDecision.highlighted
                ? Color.parseColor("#4DA3FF")
                : Color.argb(70, 255, 255, 255));
        canvas.drawPath(path, borderPaint);

        Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(dp(2));
        highlightPaint.setColor(Color.argb(styleDecision.compact ? 30 : 42, 255, 255, 255));
        canvas.drawArc(circleCenterX - circleRadius * 0.72f,
                circleCenterY - circleRadius * 0.72f,
                circleCenterX + circleRadius * 0.15f,
                circleCenterY + circleRadius * 0.10f,
                200f,
                70f,
                false,
                highlightPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(ContextCompat.getColor(mContext, android.R.color.white));
        textPaint.setTextSize(styleDecision.compact ? dp(12) : dp(14));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        Rect bounds = new Rect();
        String safeTitle = title == null ? "" : title;
        while (safeTitle.length() > 4) {
            safeTitle = safeTitle.substring(0, safeTitle.length() - 1);
        }
        while (safeTitle.length() > 1) {
            textPaint.getTextBounds(safeTitle, 0, safeTitle.length(), bounds);
            if (bounds.width() <= w - dp(styleDecision.compact ? 14 : 18)) {
                break;
            }
            safeTitle = safeTitle.substring(0, safeTitle.length() - 1);
        }
        textPaint.getTextBounds(safeTitle, 0, safeTitle.length(), bounds);
        canvas.drawText(safeTitle, circleCenterX, circleCenterY + bounds.height() / 2f, textPaint);

        if (styleDecision.largeParcel) {
            Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            badgePaint.setStyle(Paint.Style.FILL);
            badgePaint.setColor(Color.parseColor("#FF8C00"));
            float badgeRadius = dp(styleDecision.compact ? 7 : 8);
            float badgeCx = w - badgeRadius - dp(3);
            float badgeCy = badgeRadius + dp(3);
            canvas.drawCircle(badgeCx, badgeCy, badgeRadius, badgePaint);

            Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            badgeTextPaint.setColor(Color.WHITE);
            badgeTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            badgeTextPaint.setTextSize(dp(styleDecision.compact ? 8 : 9));
            badgeTextPaint.setTextAlign(Paint.Align.CENTER);
            Rect badgeBounds = new Rect();
            badgeTextPaint.getTextBounds("L", 0, 1, badgeBounds);
            canvas.drawText("L", badgeCx, badgeCy + badgeBounds.height() / 2f, badgeTextPaint);
        }

        BitmapDescriptor descriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
        markerIconCache.put(cacheKey, descriptor);
        return descriptor;
    }

    private String buildMarkerCacheKey(String title, MarkerStyleDecision styleDecision) {
        String safeTitle = title == null ? "" : title;
        return safeTitle
                + '|'
                + styleDecision.colorTone.name()
                + '|'
                + (styleDecision.compact ? 'c' : 'n')
                + '|'
                + (styleDecision.highlighted ? 'h' : 'd')
                + '|'
                + (styleDecision.largeParcel ? 'l' : 'n');
    }

    private int resolveMarkerColor(MarkerColorTone tone) {
        switch (tone) {
            case UNSCANNED:
                return Color.parseColor("#F59E0B");
            case SCANNED:
                return Color.parseColor("#169B62");
            case DELIVERING:
            default:
                return Color.parseColor("#202124");
        }
    }

    private int dp(int value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }

    @Override
    protected boolean shouldRenderAsCluster(Cluster<T> cluster) {
        return clusteringEnabled && cluster.getSize() > 1 && mZoomLevel < 18;
    }
}
