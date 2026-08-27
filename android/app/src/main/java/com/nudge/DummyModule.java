package com.nudge;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class DummyModule extends ReactContextBaseJavaModule {
    DummyModule(ReactApplicationContext context) {
        super(context);
    }

    @NonNull
    @Override
    public String getName() {
        return "DummyModule";
    }

    @ReactMethod
    public void shout() {
        Log.d("DummyModule","I am Dooom-uh");
    }
}