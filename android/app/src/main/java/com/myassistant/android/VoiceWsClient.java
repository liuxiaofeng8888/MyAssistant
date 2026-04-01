package com.myassistant.android;

import android.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;

public class VoiceWsClient {
  public interface Listener {
    void onLog(String line);
    void onMessage(JSONObject json);
    void onState(boolean connected);
  }

  private final OkHttpClient client;
  private final Listener listener;

  private WebSocket ws;
  private volatile boolean connected = false;
  private volatile String currentClientMsgId = null;
  private volatile int seq = 1;

  public VoiceWsClient(Listener listener) {
    this.listener = listener;
    this.client = new OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build();
  }

  public boolean isConnected() {
    return connected;
  }

  public void connect(String wsUrl) {
    disconnect();

    Request req = new Request.Builder()
        .url(wsUrl)
        .build();

    ws = client.newWebSocket(req, new WebSocketListener() {
      @Override
      public void onOpen(WebSocket webSocket, Response response) {
        connected = true;
        log("WS connected");
        listener.onState(true);
      }

      @Override
      public void onMessage(WebSocket webSocket, String text) {
        try {
          JSONObject j = new JSONObject(text);
          listener.onMessage(j);
        } catch (Exception e) {
          log("WS message parse error: " + e.getMessage() + " raw=" + text);
        }
      }

      @Override
      public void onClosing(WebSocket webSocket, int code, String reason) {
        log("WS closing: " + code + " " + reason);
      }

      @Override
      public void onClosed(WebSocket webSocket, int code, String reason) {
        connected = false;
        log("WS closed: " + code + " " + reason);
        listener.onState(false);
      }

      @Override
      public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        connected = false;
        log("WS failure: " + t.getMessage());
        listener.onState(false);
      }
    });
  }

  public void disconnect() {
    if (ws != null) {
      try {
        ws.close(1000, "bye");
      } catch (Exception ignored) {}
      ws = null;
    }
    connected = false;
    listener.onState(false);
  }

  public String startTurn() {
    if (ws == null) return null;
    currentClientMsgId = "c-" + UUID.randomUUID();
    seq = 1;
    JSONObject j = new JSONObject();
    try {
      j.put("type", "start");
      j.put("client_msg_id", currentClientMsgId);
      j.put("mode", "voice");
      j.put("lang", "zh-CN");
      JSONObject vad = new JSONObject();
      vad.put("enable", true);
      j.put("vad", vad);
    } catch (Exception ignored) {}
    ws.send(j.toString());
    log(">> start " + currentClientMsgId);
    return currentClientMsgId;
  }

  public void sendAudio(byte[] chunk, int len) {
    if (ws == null || currentClientMsgId == null) return;
    String b64 = Base64.encodeToString(chunk, 0, len, Base64.NO_WRAP);
    JSONObject j = new JSONObject();
    try {
      j.put("type", "audio");
      j.put("client_msg_id", currentClientMsgId);
      j.put("seq", seq++);
      j.put("data_b64", b64);
    } catch (Exception ignored) {}
    ws.send(j.toString());
  }

  public void stopTurn() {
    if (ws == null || currentClientMsgId == null) return;
    JSONObject j = new JSONObject();
    try {
      j.put("type", "stop");
      j.put("client_msg_id", currentClientMsgId);
    } catch (Exception ignored) {}
    ws.send(j.toString());
    log(">> stop " + currentClientMsgId);
    currentClientMsgId = null;
  }

  public void cancelTurn() {
    if (ws == null) return;
    JSONObject j = new JSONObject();
    try {
      j.put("type", "cancel");
      j.put("client_msg_id", currentClientMsgId);
    } catch (Exception ignored) {}
    ws.send(j.toString());
    log(">> cancel");
    currentClientMsgId = null;
  }

  private void log(String s) {
    if (listener != null) listener.onLog(s);
  }
}

