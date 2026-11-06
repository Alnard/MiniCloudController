package com.minicloud.controller;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public class ControlAccessibilityService extends AccessibilityService {
    public static volatile ControlAccessibilityService instance;

    @Override protected void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public boolean tap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                .build();
        return dispatchGesture(gesture, null, null);
    }

    public boolean swipe(float x1, float y1, float x2, float y2, long duration) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(100, Math.min(duration, 1500))))
                .build();
        return dispatchGesture(gesture, null, null);
    }
}
