package com.nudge;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

// Leftover test module from early setup. Not used by any real feature — safe to delete.
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
