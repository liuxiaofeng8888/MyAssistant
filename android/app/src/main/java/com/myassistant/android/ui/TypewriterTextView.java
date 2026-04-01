package com.myassistant.android.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * 简单打字机效果 TextView。
 * - typeTo(): 把目标文本按字符逐步输出
 * - startDots(): 无内容时的“……”占位动画
 */
public final class TypewriterTextView extends AppCompatTextView {
  private final Handler ui = new Handler(Looper.getMainLooper());

  private @Nullable Runnable ticker;
  private @Nullable String target;
  private int index = 0;

  public TypewriterTextView(Context context) {
    super(context);
  }

  public TypewriterTextView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public TypewriterTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void stopAll() {
    if (ticker != null) {
      ui.removeCallbacks(ticker);
      ticker = null;
    }
    target = null;
    index = 0;
  }

  public void setTextImmediate(@Nullable String text) {
    stopAll();
    setText(text == null ? "" : text);
  }

  public void typeTo(@Nullable String text, int msPerChar, int startDelayMs) {
    stopAll();
    target = (text == null) ? "" : text;
    index = 0;

    ticker = new Runnable() {
      @Override
      public void run() {
        if (target == null) return;
        if (index >= target.length()) {
          setText(target);
          stopAll();
          return;
        }
        index++;
        setText(target.substring(0, index));
        ui.postDelayed(this, Math.max(8, msPerChar));
      }
    };
    ui.postDelayed(ticker, Math.max(0, startDelayMs));
  }

  public void startDots(int intervalMs) {
    stopAll();
    ticker = new Runnable() {
      int i = 0;
      @Override
      public void run() {
        i = (i + 1) % 4; // 0..3
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < i; k++) sb.append('·');
        setText(sb.toString());
        ui.postDelayed(this, Math.max(120, intervalMs));
      }
    };
    ui.post(ticker);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    stopAll();
  }
}

