package com.chathead;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;
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

import java.io.IOException;

public class ChatHeadModule extends ReactContextBaseJavaModule {
  public static final String NAME = "ChatHead";
  private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
  private final ReactApplicationContext context;
  private WindowManager windowManager;
  private View chatHeadView;
  private WindowManager.LayoutParams params;
  private boolean isOverlayPermissionGranted = false;
  Boolean isOpen = false;

  public ChatHeadModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
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
  public void startMainActivity() {
    Context context = getReactApplicationContext();
    PackageManager packageManager = context.getPackageManager();
    Intent intent = packageManager.getLaunchIntentForPackage("com.viaaurea.webWrapper");
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    } else {
        // Zpracujte případ, kdy balíček nebyl nalezen
        Log.e("ChatHeadModule", "Package not found!");
    }
  }

  private void sendEvent(ReactApplicationContext reactContext, String eventName, @Nullable WritableMap params) {
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  private void RunHandler() {
    isOpen = true;
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        if(chatHeadView == null){

        
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

          chatHeadView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private int lastAction;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

              WritableMap eventData = Arguments.createMap();
              eventData.putString("action", event.getAction());

              sendEvent(context, "onButtonClicked", eventData);

              switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                  //remember the initial position.
                  initialX = params.x;
                  initialY = params.y;
                  //get the touch location
                  initialTouchX = event.getRawX();
                  initialTouchY = event.getRawY();
                  lastAction = event.getAction();
                  return true;
                case MotionEvent.ACTION_BUTTON_PRESS:
                  //As we implemented on touch listener with ACTION_MOVE,
                  //we have to check if the previous action was ACTION_DOWN
                  //to identify if the user clicked the view or not.
                  
                  //Open the chat conversation click.
                  sendEvent(context, "onButtonClicked", null);
                    // Activity activity = getCurrentActivity();
                    // startMainActivity();
                    //close the service and remove the chat heads
                  lastAction = event.getAction();
                  return true;
                case MotionEvent.ACTION_MOVE:
                  //Calculate the X and Y coordinates of the view.
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

          ImageView chatHeadImage = chatHeadView.findViewById(context.getResources().getIdentifier("chat_head_profile_iv","id",context.getPackageName()));
          windowManager.addView(chatHeadView, params);
        }
      }
    });
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
