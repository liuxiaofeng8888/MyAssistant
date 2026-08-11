package com.myassistant.android.multimodal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 图片采集工具：拍照 / 相册选取 → Base64 编码。
 *
 * <p>使用方式：
 * <pre>{@code
 *   CameraImageCapture capture = new CameraImageCapture();
 *   // 拍照
 *   capture.takePhoto(activity, launcher);
 *   // 选图
 *   capture.pickFromGallery(launcher);
 *   // 在 onActivityResult / launcher callback 中：
 *   capture.handleResult(uri, (b64, mime) -> { ... });
 * }</pre>
 */
public final class CameraImageCapture {

  /** 压缩后图片长边最大像素数 */
  private int maxSize = 2048;

  /** JPEG 压缩质量 (0-100) */
  private int jpegQuality = 80;

  public CameraImageCapture() {}

  public CameraImageCapture(int maxSize, int jpegQuality) {
    this.maxSize = maxSize;
    this.jpegQuality = jpegQuality;
  }

  /**
   * 启动系统相机拍照。
   *
   * @param activity 当前的 Activity
   * @param launcher ActivityResultLauncher，用于接收拍照结果
   */
  public void takePhoto(@NonNull Activity activity, @NonNull ActivityResultLauncher<Intent> launcher) {
    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    if (intent.resolveActivity(activity.getPackageManager()) != null) {
      launcher.launch(intent);
    }
  }

  /**
   * 从系统相册选取图片。
   */
  public void pickFromGallery(@NonNull ActivityResultLauncher<Intent> launcher) {
    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
    launcher.launch(intent);
  }

  /**
   * 处理拍照/选图返回的 Uri，转为 Base64 编码字符串。
   *
   * @param uri      图片 URI（拍照可能为 null，需从 Intent extras 取 Bitmap）
   * @param data     拍照返回的 Intent（相册选取时可为 null）
   * @param callback 结果回调
   */
  public void handleResult(@Nullable Uri uri, @Nullable Intent data,
                           @NonNull Activity activity,
                           @NonNull Callback callback) {
    try {
      Bitmap bitmap = decodeBitmap(uri, data, activity);
      if (bitmap == null) {
        callback.onError("无法读取图片");
        return;
      }

      // 压缩尺寸
      Bitmap resized = resizeIfNeeded(bitmap);
      if (resized != bitmap) {
        bitmap.recycle();
      }

      // 编码为 JPEG Base64
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      resized.compress(Bitmap.CompressFormat.JPEG, jpegQuality, bos);
      byte[] bytes = bos.toByteArray();
      String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
      resized.recycle();

      callback.onImageEncoded(b64, "image/jpeg", bytes.length);
    } catch (Exception e) {
      callback.onError("图片处理失败: " + e.getMessage());
    }
  }

  // ---- 内部方法 ----

  @Nullable
  private Bitmap decodeBitmap(@Nullable Uri uri, @Nullable Intent data,
                              @NonNull Activity activity) throws Exception {
    // 拍照返回：从 data extras 获取缩略图
    if (data != null && data.getExtras() != null) {
      Bitmap bmp = (Bitmap) data.getExtras().get("data");
      if (bmp != null) return bmp;
    }
    // 从 URI 读取全尺寸图
    if (uri != null) {
      try (InputStream is = activity.getContentResolver().openInputStream(uri)) {
        return BitmapFactory.decodeStream(is);
      }
    }
    return null;
  }

  private Bitmap resizeIfNeeded(@NonNull Bitmap src) {
    int w = src.getWidth();
    int h = src.getHeight();
    int max = Math.max(w, h);
    if (max <= maxSize) return src;

    float scale = (float) maxSize / max;
    int newW = Math.round(w * scale);
    int newH = Math.round(h * scale);
    return Bitmap.createScaledBitmap(src, newW, newH, true);
  }

  // ---- 回调接口 ----

  public interface Callback {
    /** @param b64      Base64 编码的图片数据 */
    /** @param mime     MIME 类型 */
    /** @param byteSize 原始字节数（压缩后） */
    void onImageEncoded(@NonNull String b64, @NonNull String mime, int byteSize);
    void onError(@NonNull String message);
  }
}
