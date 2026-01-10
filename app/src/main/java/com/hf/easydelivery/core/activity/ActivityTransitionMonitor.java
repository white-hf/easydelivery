package com.hf.easydelivery.core.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.hf.courierservice.apihelper.FileLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ActivityTransitionMonitor {
    public interface Listener {
        void onActivityTransition(int activityType, int transitionType);
    }

    private static final AtomicReference<WeakReference<Listener>> LISTENER_REF =
            new AtomicReference<>(new WeakReference<>(null));

    private final Context appContext;
    private final ActivityRecognitionClient activityRecognitionClient;
    private final android.app.PendingIntent pendingIntent;
    private boolean registered;

    public ActivityTransitionMonitor(@NonNull Context context, @Nullable Listener listener) {
        this.appContext = context.getApplicationContext();
        this.activityRecognitionClient = ActivityRecognition.getClient(appContext);
        Intent intent = new Intent(appContext, ActivityTransitionReceiver.class);
        this.pendingIntent = android.app.PendingIntent.getBroadcast(appContext,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        setListener(listener);
    }

    public void setListener(@Nullable Listener listener) {
        LISTENER_REF.set(new WeakReference<>(listener));
    }

    public void register() {
        if (registered) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ActivityCompat.checkSelfPermission(appContext,
                        Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                    FileLog.getInstance().warning(ActivityTransitionMonitor.class.getSimpleName(),
                            "registerActivityTransitionUpdates skipped: ACTIVITY_RECOGNITION not granted");
                    return;
                }
            }
            ActivityTransitionRequest request = new ActivityTransitionRequest(getTransitions());
            activityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent);
            registered = true;
        } catch (SecurityException se) {
            FileLog.getInstance().warning(ActivityTransitionMonitor.class.getSimpleName(),
                    "registerActivityTransitionUpdates failed: " + se.getMessage());
        }
    }

    public void unregister() {
        if (!registered) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ActivityCompat.checkSelfPermission(appContext,
                        Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent);
            registered = false;
        } catch (SecurityException se) {
            FileLog.getInstance().warning(ActivityTransitionMonitor.class.getSimpleName(),
                    "unregisterActivityTransitionUpdates failed: " + se.getMessage());
        }
    }

    private static List<ActivityTransition> getTransitions() {
        List<ActivityTransition> transitions = new ArrayList<>();
        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());
        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build());
        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());
        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build());
        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.RUNNING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build());
        transitions.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.RUNNING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build());
        return transitions;
    }

    private static void dispatch(int activityType, int transitionType) {
        WeakReference<Listener> ref = LISTENER_REF.get();
        Listener listener = ref != null ? ref.get() : null;
        if (listener != null) {
            listener.onActivityTransition(activityType, transitionType);
        }
    }

    public static class ActivityTransitionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ActivityTransitionResult.hasResult(intent)) {
                ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
                for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                    handleActivityTransition(event.getActivityType(), event.getTransitionType());
                }
            }
        }

        private void handleActivityTransition(int activityType, int transitionType) {
            Log.d("ActivityTransition",
                    "Activity: " + activityType + ", Transition: " + transitionType);
            ActivityTransitionMonitor.dispatch(activityType, transitionType);
        }
    }
}
