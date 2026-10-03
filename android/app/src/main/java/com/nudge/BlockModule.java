package com.nudge;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

/**
 * Lets the home screen's two Blockbutton toggles read and set BlockState - which apps
 * TrackerService should immediately back out of every time it counts a reel from them.
 */
public class BlockModule extends ReactContextBaseJavaModule {

    BlockModule(ReactApplicationContext context) {
        super(context);
    }

    @NonNull
    @Override
    public String getName() {
        return "BlockModule";
    }

    @ReactMethod
    public void isBlocked(String pkg, Promise promise) {
        promise.resolve(BlockState.isBlocked(getReactApplicationContext(), pkg));
    }

    @ReactMethod
    public void setBlocked(String pkg, boolean blocked) {
        BlockState.setBlocked(getReactApplicationContext(), pkg, blocked);
    }
}
