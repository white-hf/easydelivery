package com.hf.easydelivery.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class SignatureView extends View {

    private Paint paint;
    private Path path;
    private Bitmap bitmap;
    private Canvas canvas;

    public SignatureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(10f);
        path = new Path();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.canvas != null) {
            canvas.drawPath(path, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                path.moveTo(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                path.lineTo(x, y);
                break;
            case MotionEvent.ACTION_UP:
                if (canvas != null) {
                    canvas.drawPath(path, paint);
                }
                // path.reset(); // Don't reset path here, keep it for drawing
                break;
            default:
                return false;
        }
        invalidate();
        return true;
    }

    public void clear() {
        path.reset();
        if (bitmap != null) {
            bitmap.eraseColor(Color.TRANSPARENT);
            if (canvas != null) {
                canvas.setBitmap(bitmap);
            }
        }
        invalidate();
    }

    public Bitmap getSignatureBitmap() {
        if (bitmap == null)
            return null;
        // Draw the path onto the bitmap one last time to ensure everything is captured
        if (canvas != null) {
            canvas.drawPath(path, paint);
        }
        return bitmap;
    }
}
