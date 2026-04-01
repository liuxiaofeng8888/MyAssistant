package com.myassistant.android.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.myassistant.android.R;

public final class ResultOverlayDialog extends Dialog {
  public ResultOverlayDialog(@NonNull Context context, @NonNull String text) {
    super(context);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.dialog_result_overlay);
    setCancelable(true);
    setCanceledOnTouchOutside(true);

    TextView resultText = findViewById(R.id.resultText);
    Button btnClose = findViewById(R.id.btnClose);
    resultText.setText(text);
    btnClose.setOnClickListener(v -> dismiss());

    Window w = getWindow();
    if (w != null) {
      w.setDimAmount(0f);
      w.setBackgroundDrawable(makeCardBackground());
      w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
      WindowManager.LayoutParams lp = w.getAttributes();
      lp.gravity = Gravity.BOTTOM;
      lp.y = dp(context, 18);
      w.setAttributes(lp);
    }
  }

  private static GradientDrawable makeCardBackground() {
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.parseColor("#F21A1A1A"));
    d.setCornerRadius(28f);
    d.setStroke(1, Color.parseColor("#33FFFFFF"));
    return d;
  }

  private static int dp(Context c, int v) {
    float d = c.getResources().getDisplayMetrics().density;
    return Math.round(v * d);
  }
}

