package com.hf.easydelivery.common;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.hf.courierservice.apihelper.FileLog;

/**
 * Utility class for centralized handling of runtime permissions.
 * Provides methods to check and request permissions such as Camera, Location, Storage, and Microphone.
 */
public class PermissionUtils {

    public static final int REQ_CAMERA = 100;
    public static final int REQ_LOCATION = 101;
    public static final int REQ_STORAGE = 102;
    public static final int REQ_MICROPHONE = 103;

    public static boolean ensureCameraPermission(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            FileLog.getInstance().debug("PermissionUtils", "Camera permission requested");
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.CAMERA}, REQ_CAMERA);
            return false;
        }
        FileLog.getInstance().debug("PermissionUtils", "Camera permission granted");
        return true;
    }

    public static boolean ensureLocationPermission(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            FileLog.getInstance().debug("PermissionUtils", "Location permission requested");
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return false;
        }
        FileLog.getInstance().debug("PermissionUtils", "Location permission granted");
        return true;
    }

    public static boolean ensureStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                FileLog.getInstance().debug("PermissionUtils", "Storage permission requested");
                ActivityCompat.requestPermissions(activity,
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
                return false;
            }
        }
        FileLog.getInstance().debug("PermissionUtils", "Storage permission granted");
        return true;
    }

    public static boolean ensureMicrophonePermission(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            FileLog.getInstance().debug("PermissionUtils", "Microphone permission requested");
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_MICROPHONE);
            return false;
        }
        FileLog.getInstance().debug("PermissionUtils", "Microphone permission granted");
        return true;
    }

    public static boolean hasCameraPermission(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestCameraPermission(Activity act, int requestCode) {
        ActivityCompat.requestPermissions(act,
                new String[]{android.Manifest.permission.CAMERA},
                requestCode);
        FileLog.getInstance().debug("PermissionUtils", "Camera permission requested with code " + requestCode);
    }

    public static boolean isPermissionGranted(int[] grantResults) {
        return grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }
}