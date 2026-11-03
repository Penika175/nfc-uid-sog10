package com.local.nfcuid;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Shape-morphing loading indicator in the spirit of the Material 3 Expressive
 * one: the outline cycles through a fixed list of rounded shapes, morphing
 * between neighbours while the whole thing rotates slowly.
 */
public class ShapeLoadingView extends View {

    private static final int SHAPES = 7;
    private static final int POINTS = 32;
    private static final long SEGMENT_MS = 650L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float t = 0f;
    private ValueAnimator animator;

    public ShapeLoadingView(Context context) {
        super(context);
        init();
    }

    public ShapeLoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
    }

    public void setShapeColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void start() {
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SHAPES * SEGMENT_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    /** 0 = circle, otherwise a regular polygon with that many sides. */
    private static int sidesOf(int shape) {
        switch (shape) {
            case 0:
                return 0;
            case 1:
                return 3;
            case 2:
                return 4;
            case 3:
                return 5;
            case 4:
                return 6;
            case 5:
                return 8;
            default:
                return 0;
        }
    }

    private static float radiusAt(int shape, double ang) {
        int n = sidesOf(shape);
        if (n < 3) return 1f;
        double seg = 2 * Math.PI / n;
        double a = ang % seg;
        if (a > seg / 2) a = seg - a;
        return (float) (Math.cos(Math.PI / n) / Math.cos(a - Math.PI / n));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float maxR = Math.min(w, h) / 2f * 0.80f;

        float cycle = t * SHAPES;
        int idx = ((int) cycle) % SHAPES;
        int next = (idx + 1) % SHAPES;
        float p = cycle - (float) (int) cycle;
        p = p * p * (3f - 2f * p); // smooth step

        float rotation = (float) Math.toRadians(t * 360f);
        float pulse = 0.90f + 0.10f * (float) Math.sin(2 * Math.PI * t * 3);

        float[] r = new float[POINTS];
        for (int i = 0; i < POINTS; i++) {
            double ang = 2 * Math.PI * i / POINTS;
            float a = radiusAt(idx, ang);
            float b = radiusAt(next, ang);
            r[i] = a + (b - a) * p;
        }

        // two smoothing passes turn sharp polygon corners into rounded ones
        for (int pass = 0; pass < 2; pass++) {
            float[] tmp = r.clone();
            for (int i = 0; i < POINTS; i++) {
                r[i] = (tmp[(i - 1 + POINTS) % POINTS]
                        + 2f * tmp[i]
                        + tmp[(i + 1) % POINTS]) / 4f;
            }
        }

        path.reset();
        for (int i = 0; i < POINTS; i++) {
            double ang = 2 * Math.PI * i / POINTS + rotation;
            float rr = maxR * pulse * r[i];
            float x = cx + (float) (rr * Math.cos(ang));
            float y = cy + (float) (rr * Math.sin(ang));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        canvas.drawPath(path, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }
}
