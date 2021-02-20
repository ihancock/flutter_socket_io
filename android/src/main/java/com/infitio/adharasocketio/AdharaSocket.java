package com.infitio.adharasocketio;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Manager;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;


class AdharaSocket implements MethodCallHandler {

  final Socket socket;
  private final MethodChannel channel;
  private static final String TAG = "Adhara:Socket";
  private Options options;
  private static Manager manager;
  HashMap<String, Integer> eventListenerCount = new HashMap<>();

  void log(String message) {
    if (this.options.enableLogging) {
      Log.d(TAG, message);
    }
  }

  private AdharaSocket(MethodChannel channel, Options options) {
    this.channel = channel;
    this.options = options;
    log("Connecting to... " + options.uri);
    socket = AdharaSocket.manager.socket(options.namespace);
  }

  static AdharaSocket getInstance(Registrar registrar, Options options) throws URISyntaxException {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "adhara_socket_io:socket:" + String.valueOf(options.index));
    // we create new manager instance every time here
    // because manager cannot update the uri
    AdharaSocket.manager = new Manager(new URI(options.uri), options);
    AdharaSocket _socket = new AdharaSocket(channel, options);
    channel.setMethodCallHandler(_socket);
    return _socket;
  }

  // https://github.com/flutter/flutter/issues/34993#issue-459900986
  // https://github.com/aloisdeniel/flutter_geocoder/commit/bc34cfe473bfd1934fe098bb7053248b75200241
  private static class MethodResultWrapper implements MethodChannel.Result {
    private MethodChannel.Result methodResult;
    private Handler handler;

    MethodResultWrapper(MethodChannel.Result result) {
      methodResult = result;
      handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void success(final Object result) {
      handler.post(() -> methodResult.success(result));
    }

    @Override
    public void error(
      final String errorCode, final String errorMessage, final Object errorDetails) {
      handler.post(() -> methodResult.error(errorCode, errorMessage, errorDetails));
    }

    @Override
    public void notImplemented() {
      handler.post(() -> methodResult.notImplemented());
    }
  }

  @Override
  public void onMethodCall(MethodCall call, Result rawResult) {
    MethodChannel.Result result = new MethodResultWrapper(rawResult);
    switch (call.method) {
      case "connect": {
        log("Connecting....");
        socket.connect();
        result.success(null);
        break;
      }
      case "emit": {
        final String eventName = call.argument("eventName");
        final List data = call.argument("arguments");
        final String reqId = call.argument("reqId");
        log("emitting:::" + data + ":::to:::" + eventName);
        Object[] array = {};
        if (data != null) {
          array = new Object[data.size()];
          for (int i = 0; i < data.size(); i++) {
            Object datum = data.get(i);
            System.out.println(datum);
            System.out.println(datum == null ? "null" : datum.getClass());
            if (datum instanceof Map) {
              array[i] = new JSONObject((Map) datum);
            } else if (datum instanceof Collection) {
              array[i] = new JSONArray((Collection) datum);
            } else {
              array[i] = datum;
                            /*try{
                                array[i] = new JSONObject(datum.toString());
                            }catch (JSONException jse){
                                try{
                                    array[i] = new JSONArray(datum.toString());
                                }catch (JSONException jse2){
                                    array[i] = datum;
                                }
                            }*/
            }
          }
        }
        if (reqId == null) {
          socket.emit(eventName, array);
        } else {
          socket.emit(eventName, array, args -> {
              log("Ack received:::" + eventName + ":::" + Arrays.toString(args));
              final Map<String, Object> arguments = new HashMap<>();
              arguments.put("reqId", reqId);
              List<Object> argsList = new ArrayList<>();
              for (Object arg : args) {
                if ((arg instanceof JSONObject)
                  || (arg instanceof JSONArray)) {
                  argsList.add(arg.toString());
                } else {
                  argsList.add(arg);
                }
              }
              arguments.put("args", argsList);
              final Handler handler = new Handler(Looper.getMainLooper());
              handler.post(() -> channel.invokeMethod("incomingAck", arguments));
            }
          );
        }
        result.success(null);
        break;
      }
      case "isConnected": {
        log("connected:::");
        result.success(socket.connected());
        break;
      }
      case "disconnect": {
        log("disconnected:::");
        socket.disconnect();
        result.success(null);
        break;
      }
      default: {
        result.notImplemented();
      }
    }
  }

  static class Options extends IO.Options {

    String uri;
    String namespace = "/";
    int index;
    Boolean enableLogging = false;

    Options(int index, String uri) {
      this.index = index;
      this.uri = uri;
    }

  }

}
