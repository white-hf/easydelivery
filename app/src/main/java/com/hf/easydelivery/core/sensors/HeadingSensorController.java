package com.hf.easydelivery.core.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.courierservice.apihelper.FileLog;

public final class HeadingSensorController {
    public interface Listener {
        void onMotionWake(long nowUptime);
    }

    private static final String TAG = "HeadingSensorController";
    private static final float HEADING_ALPHA = 0.2f;
    private static final float ACCEL_WAKE_THRESHOLD = 0.8f;
    private static final int ACCEL_REQUIRED_HITS = 4;
    private static final long ACCEL_WINDOW_MS = 400L;
    private static final long MOTION_WAKE_COOLDOWN_MS = 3_000L;
    private static final int ACCEL_BUFFER_SIZE = 10;
    private static final float NOISE_STD_MULTIPLIER = 2.5f;

    private final SensorManager sensorManager;
    private final Sensor rotationVectorSensor;
    private final Sensor linearAccelerationSensor;
    private final Sensor accelerometerSensor;
    private final Listener listener;

    private boolean active;
    private int headingAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    private float currentHeadingDegrees = Float.NaN;

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private long lastAccelSpikeUptime = 0L;
    private long lastMotionWakeUptime = 0L;
    private final float[] accelBuffer = new float[ACCEL_BUFFER_SIZE];
    private int accelBufferIndex = 0;
    private boolean accelBufferFilled = false;
    private int accelConsecutiveHits = 0;

    public HeadingSensorController(@NonNull Context context, @Nullable Listener listener) {
        this.listener = listener;
        sensorManager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            if (linearAccelerationSensor == null) {
                accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                FileLog.i(TAG, "Linear acceleration sensor missing, using basic accelerometer as fallback");
            } else {
                accelerometerSensor = null;
            }
        } else {
            rotationVectorSensor = null;
            linearAccelerationSensor = null;
            accelerometerSensor = null;
        }
    }

    public void setActive(boolean enable) {
        if (active == enable) {
            return;
        }
        active = enable;
        if (active) {
            startHeadingUpdates();
            startMotionWakeMonitoring();
        } else {
            stopHeadingUpdates();
            stopMotionWakeMonitoring();
        }
    }

    public boolean hasReliableHeading() {
        return !Float.isNaN(currentHeadingDegrees)
                && headingAccuracy != SensorManager.SENSOR_STATUS_UNRELIABLE;
    }

    public float getCurrentHeadingDegrees() {
        return currentHeadingDegrees;
    }

    public long getLastMotionWakeUptime() {
        return lastMotionWakeUptime;
    }

    private void startHeadingUpdates() {
        if (sensorManager != null && rotationVectorSensor != null) {
            sensorManager.registerListener(headingListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void stopHeadingUpdates() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(headingListener);
        }
    }

    private void startMotionWakeMonitoring() {
        if (sensorManager != null) {
            if (linearAccelerationSensor != null) {
                sensorManager.registerListener(accelListener, linearAccelerationSensor,
                        SensorManager.SENSOR_DELAY_UI);
            } else if (accelerometerSensor != null) {
                sensorManager.registerListener(accelListener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    private void stopMotionWakeMonitoring() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(accelListener);
        }
        accelConsecutiveHits = 0;
    }

    private final SensorEventListener headingListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientationAngles);
                float azimuthRad = orientationAngles[0];
                float azimuthDeg = (float) Math.toDegrees(azimuthRad);
                if (azimuthDeg < 0) {
                    azimuthDeg += 360f;
                }
                if (Float.isNaN(currentHeadingDegrees)) {
                    currentHeadingDegrees = azimuthDeg;
                } else {
                    currentHeadingDegrees = lowPassHeading(azimuthDeg, currentHeadingDegrees, HEADING_ALPHA);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            headingAccuracy = accuracy;
        }
    };

    private final SensorEventListener accelListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            int type = event.sensor.getType();
            if (type != Sensor.TYPE_LINEAR_ACCELERATION && type != Sensor.TYPE_ACCELEROMETER) {
                return;
            }

            float ax = event.values[0];
            float ay = event.values[1];
            float az = event.values[2];
            float magnitude = (float) Math.sqrt(ax * ax + ay * ay + az * az);

            if (type == Sensor.TYPE_ACCELEROMETER) {
                magnitude = Math.abs(magnitude - 9.81f);
            }

            accelBuffer[accelBufferIndex] = magnitude;
            accelBufferIndex = (accelBufferIndex + 1) % ACCEL_BUFFER_SIZE;
            if (!accelBufferFilled && accelBufferIndex == 0) {
                accelBufferFilled = true;
            }

            float threshold = ACCEL_WAKE_THRESHOLD;
            if (accelBufferFilled) {
                float mean = 0f;
                for (float val : accelBuffer) {
                    mean += val;
                }
                mean /= ACCEL_BUFFER_SIZE;

                float variance = 0f;
                for (float val : accelBuffer) {
                    float diff = val - mean;
                    variance += diff * diff;
                }
                float std = (float) Math.sqrt(variance / ACCEL_BUFFER_SIZE);
                threshold = mean + NOISE_STD_MULTIPLIER * std;

                if (threshold < 0.6f) {
                    threshold = 0.6f;
                }
            }

            long now = SystemClock.uptimeMillis();
            if (magnitude >= threshold) {
                if (now - lastAccelSpikeUptime > ACCEL_WINDOW_MS) {
                    accelConsecutiveHits = 0;
                }
                lastAccelSpikeUptime = now;
                accelConsecutiveHits++;
                if (accelConsecutiveHits >= ACCEL_REQUIRED_HITS) {
                    accelConsecutiveHits = 0;
                    maybeDispatchMotionWake(now);
                    if (accelBufferFilled) {
                        float mean = 0f;
                        for (float val : accelBuffer) {
                            mean += val;
                        }
                        mean /= ACCEL_BUFFER_SIZE;
                        FileLog.getInstance().debug(TAG,
                                String.format("Motion wake triggered: mag=%.2f, threshold=%.2f (mean=%.2f)",
                                        magnitude, threshold, mean));
                    }
                }
            } else if (now - lastAccelSpikeUptime > ACCEL_WINDOW_MS) {
                accelConsecutiveHits = 0;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // no-op
        }
    };

    private void maybeDispatchMotionWake(long nowUptime) {
        if (nowUptime - lastMotionWakeUptime < MOTION_WAKE_COOLDOWN_MS) {
            return;
        }
        lastMotionWakeUptime = nowUptime;
        if (listener != null) {
            listener.onMotionWake(nowUptime);
        }
    }

    private static float lowPassHeading(float input, float output, float alpha) {
        float delta = input - output;
        if (Math.abs(delta) > 180f) {
            if (delta > 0f) {
                output += 360f;
            } else {
                output -= 360f;
            }
        }
        float result = output + alpha * (input - output);
        if (result >= 360f) {
            result -= 360f;
        }
        if (result < 0f) {
            result += 360f;
        }
        return result;
    }
}
