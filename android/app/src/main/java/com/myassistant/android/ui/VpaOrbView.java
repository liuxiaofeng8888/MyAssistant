package com.myassistant.android.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * 轻量 VPA 动画：中心“能量球”+ 外圈呼吸波纹。
 * 不依赖三方库，适合作为 MVP 的唤醒态动效。
 */
public final class VpaOrbView extends View {
  private final Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private float phase = 0f; // 0..1
  private @Nullable ValueAnimator animator;

  public VpaOrbView(Context context) {
    super(context);
    init();
  }

  public VpaOrbView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public VpaOrbView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    orbPaint.setStyle(Paint.Style.FILL);
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(dp(2));
    ringPaint.setColor(0x66FFFFFF);
  }

  public void start() {
    if (animator != null) return;
    animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(1400);
    animator.setRepeatCount(ValueAnimator.INFINITE);
    animator.setRepeatMode(ValueAnimator.RESTART);
    animator.addUpdateListener(a -> {
      phase = (float) a.getAnimatedValue();
      invalidate();
    });
    animator.start();
  }

  public void stop() {
    if (animator != null) {
      animator.cancel();
      animator = null;
    }
    phase = 0f;
    invalidate();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    stop();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int w = getWidth();
    int h = getHeight();
    if (w <= 0 || h <= 0) return;

    float cx = w / 2f;
    float cy = h / 2f;
    float base = Math.min(w, h) * 0.22f;

    float orbR = base * (0.92f + 0.08f * (float) Math.sin(phase * Math.PI * 2));
    float ring1 = base * (1.25f + 0.35f * phase);
    float ring2 = base * (1.45f + 0.55f * phase);

    float ringAlpha1 = 0.55f * (1f - phase);
    float ringAlpha2 = 0.35f * (1f - phase);

    LinearGradient gradient = new LinearGradient(
        cx - orbR, cy - orbR,
        cx + orbR, cy + orbR,
        new int[]{0xFF6EE7FF, 0xFF7C5CFF, 0xFFFF5CAA},
        new float[]{0f, 0.55f, 1f},
        Shader.TileMode.CLAMP
    );
    orbPaint.setShader(gradient);

    canvas.drawCircle(cx, cy, orbR, orbPaint);

    ringPaint.setAlpha((int) (255 * ringAlpha1));
    canvas.drawOval(new RectF(cx - ring1, cy - ring1, cx + ring1, cy + ring1), ringPaint);

    ringPaint.setAlpha((int) (255 * ringAlpha2));
    canvas.drawOval(new RectF(cx - ring2, cy - ring2, cx + ring2, cy + ring2), ringPaint);
  }

  private float dp(float v) {
    return v * getResources().getDisplayMetrics().density;
  }
}

