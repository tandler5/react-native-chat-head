package com.chathead;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import androidx.annotation.Nullable;


import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ComponentName;
import android.content.SharedPreferences;
import com.chathead.R;

import java.io.IOException;

public class ChatHeadModule extends ReactContextBaseJavaModule {
  public static final String NAME = "ChatHead";
  private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
  private final ReactApplicationContext context;
  private WindowManager windowManager;
  private View chatHeadView;
  private WindowManager.LayoutParams params;
  private boolean isOverlayPermissionGranted = false;
  private String packageName = "cz.smable.pos";
  private static final int MAX_CLICK_DISTANCE = 15;
  private static final int MAX_CLICK_DURATION = 1000;
  private SharedPreferences sharedPreferences;
  private long pressStartTime;
  private float pressedX;
  private float pressedY;
  private boolean stayedWithinClickDistance;
  Boolean isOpen = false;

  public ChatHeadModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
    sharedPreferences = reactContext.getSharedPreferences(NAME, Context.MODE_PRIVATE);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  // Use this method to get the current MainActivity instance
  private Activity getMainActivity() {
    return getCurrentActivity();
  }
  public void startMainActivity(String packageName, ImageView chatHeadImage) {

    Context context = getReactApplicationContext();

    if ("cz.smable.pos" == packageName) {
      chatHeadImage.setImageResource(R.drawable.logo);
    }else {
      chatHeadImage.setImageResource(R.drawable.smable);
    }

    Intent intent = new Intent(Intent.ACTION_MAIN);
    String mainActivityName = packageName + ".MainActivity";

    intent.setComponent(new ComponentName(packageName, mainActivityName));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }

  private void sendEvent(String eventName, @Nullable WritableMap params) {
    Context context = getReactApplicationContext();
    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
  }

  private void RunHandler() {
    isOpen = true;
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        String isChatHeadActive = sharedPreferences.getString("isChatHeadActive", "");

        if(isChatHeadActive != "yes"){
          SharedPreferences.Editor editor = sharedPreferences.edit();
          editor.putString("isChatHeadActive", "yes");
          editor.apply();
        
          if (windowManager == null) {
            windowManager = (WindowManager) context.getSystemService(Service.WINDOW_SERVICE);
          }

          params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
              WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
              WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
          );

          params.gravity = Gravity.TOP | Gravity.START;
          params.x = 0;
          params.y = 100;

          LayoutInflater inflater = LayoutInflater.from(context);
          chatHeadView = inflater.inflate(context.getResources().getIdentifier("chat_head_layout", "layout", context.getPackageName()), null);

          ImageView chatHeadViewLayout = chatHeadView.findViewById(context.getResources().getIdentifier("chat_head_layout", "layout", context.getPackageName()));

          ImageView chatHeadImage = chatHeadView.findViewById(context.getResources().getIdentifier("chat_head_profile_iv","id",context.getPackageName()));


          chatHeadView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private int lastAction;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

              switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                  //remember the initial position.
                  initialX = params.x;
                  initialY = params.y;
                  //get the touch location
                  pressStartTime = System.currentTimeMillis();
                  pressedX = event.getX();
                  pressedY = event.getY();
                  stayedWithinClickDistance = true;
                  initialTouchX = event.getRawX();
                  initialTouchY = event.getRawY();
                  lastAction = event.getAction();
                  return true;
                case MotionEvent.ACTION_UP:
                  long pressDuration = System.currentTimeMillis() - pressStartTime;
                  if (pressDuration < MAX_CLICK_DURATION && stayedWithinClickDistance) {
                    startMainActivity(packageName, chatHeadImage);
                    if(packageName == "cz.smable.pos"){
                      packageName = "com.viaaurea.webWrapper";
                    }else{
                      packageName = "cz.smable.pos";
                    }
                  }else if(pressDuration>=5000 && stayedWithinClickDistance){
                    sendEvent("onButtonHolded", null);
                  }

                  return true;
                case MotionEvent.ACTION_MOVE:
                  //Calculate the X and Y coordinates of the view.
                  if (stayedWithinClickDistance && distance(pressedX, pressedY, event.getX(), event.getY(), context) > MAX_CLICK_DISTANCE) {
                    stayedWithinClickDistance = false;
                  }
                  params.x = initialX + (int) (event.getRawX() - initialTouchX);
                  params.y = initialY + (int) (event.getRawY() - initialTouchY);
                  //Update the layout with new X & Y coordinate
                  windowManager.updateViewLayout(chatHeadView, params);
                  lastAction = event.getAction();
                  return true;
              }
              return false;
            }
          });

          windowManager.addView(chatHeadView, params);
        }
      }
    });
  }

  private static float distance(float x1, float y1, float x2, float y2, ReactApplicationContext reactContext) {
    float dx = x1 - x2;
    float dy = y1 - y2;
    float distanceInPx = (float) Math.sqrt(dx * dx + dy * dy);
    return pxToDp(distanceInPx, reactContext);
  }

  private static float pxToDp(float px, ReactApplicationContext reactContext) {
    return px / reactContext.getResources().getDisplayMetrics().density;
  }

 @ReactMethod
  public void requrestPermission(final Promise promise) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!Settings.canDrawOverlays(getReactApplicationContext())) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getReactApplicationContext().getPackageName()));
            getCurrentActivity().startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
        } else {
            isOverlayPermissionGranted = true;
        }
    }
    promise.resolve(isOverlayPermissionGranted);
  }

  @ReactMethod
  public void checkOverlayPermission(final Promise promise) {
    if (!Settings.canDrawOverlays(getReactApplicationContext())) {
        promise.resolve(false);
        isOverlayPermissionGranted = false;
    } else {
        promise.resolve(true);
        isOverlayPermissionGranted = true;
    }
  }

  @ReactMethod
  public void showChatHead() {
    if (isOverlayPermissionGranted && !isOpen){
      RunHandler();
    }else {
      Log.e("warning","please request Permission first");
    }
  }

  @ReactMethod
  public void hideChatHead() {
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        if (windowManager != null && chatHeadView != null && chatHeadView.isAttachedToWindow()) {
          windowManager.removeView(chatHeadView);
          chatHeadView = null;
          isOpen = false;
        }
      }
    });
  }

}
