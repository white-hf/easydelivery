package com.hf.easydelivery.view;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.R;
import com.hf.easydelivery.map.CameraFollowController;
import com.hf.easydelivery.map.DeliveryFocusManager;
import com.hf.easydelivery.map.InfoPillProximityController;
import com.hf.easydelivery.map.MapInnerFragment;
import com.hf.easydelivery.map.CameraFollowController.FollowConfig;
import com.hf.easydelivery.map.InfoPillProximityController.ProximityConfig;
import com.hf.easydelivery.map.DeliveryFocusManager.ZoomConfig;
import com.hf.easydelivery.map.config.ProfileManager;

public class DeveloperPanelBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "DevPanelBottomSheet";

    // Host Fragment
    private MapInnerFragment mapFragment;

    // UI Components
    private EditText etEnterRadius, etExitRadius, etThrottleDrive, etThrottleFoot;
    private EditText etFollowStdInterval, etFollowStdDist, etFollowStdHeading;
    private EditText etZoomCloseMeters, etZoomCloseZoom, etZoomApproachMeters, etZoomApproachZoom;
    private SwitchCompat switchInsideBoost;

    // Profile UI (optional – depends on layout availability)
    private RadioButton rbProfilePowerSaver;
    private RadioButton rbProfileAdvanced;
    private Button btnApplyProfile;

    public static DeveloperPanelBottomSheet newInstance() {
        return new DeveloperPanelBottomSheet();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Try parent first
        Fragment parent = getParentFragment();
        if (parent instanceof MapInnerFragment) {
            mapFragment = (MapInnerFragment) parent;
            return;
        }
        // Fallback to target fragment if set
        Fragment target = getTargetFragment();
        if (target instanceof MapInnerFragment) {
            mapFragment = (MapInnerFragment) target;
            return;
        }
        // No map fragment available: operate in limited mode (only Proximity section is active)
        mapFragment = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_developer_panel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        populateUiWithCurrentConfigs();
        setupClickListeners(view);
        setupProfileSection(view);
    }

    private void bindViews(@NonNull View view) {
        // Proximity
        etEnterRadius = view.findViewById(R.id.et_enter_radius);
        etExitRadius = view.findViewById(R.id.et_exit_radius);
        etThrottleDrive = view.findViewById(R.id.et_throttle_drive);
        etThrottleFoot = view.findViewById(R.id.et_throttle_foot);

        // Follow
        etFollowStdInterval = view.findViewById(R.id.et_follow_std_interval);
        etFollowStdDist = view.findViewById(R.id.et_follow_std_dist);
        etFollowStdHeading = view.findViewById(R.id.et_follow_std_heading);

        // Zoom
        etZoomCloseMeters = view.findViewById(R.id.et_zoom_close_meters);
        etZoomCloseZoom = view.findViewById(R.id.et_zoom_close_zoom);
        etZoomApproachMeters = view.findViewById(R.id.et_zoom_approach_meters);
        etZoomApproachZoom = view.findViewById(R.id.et_zoom_approach_zoom);
        switchInsideBoost = view.findViewById(R.id.switch_inside_boost);
    }

    private void setupProfileSection(@NonNull View view) {
        try {
            rbProfilePowerSaver = view.findViewById(R.id.rb_profile_powersaver);
            rbProfileAdvanced   = view.findViewById(R.id.rb_profile_advanced);
            btnApplyProfile     = view.findViewById(R.id.btn_apply_profile);

            final Context ctx = requireContext();
            final ProfileManager pm = ProfileManager.get(ctx);
            final ProfileManager.AppProfile current = pm.getCurrent();

            // Reflect current profile (if widgets exist)
            if (rbProfilePowerSaver != null) {
                rbProfilePowerSaver.setChecked(current == ProfileManager.AppProfile.POWERSAVER);
            }
            if (rbProfileAdvanced != null) {
                rbProfileAdvanced.setChecked(current == ProfileManager.AppProfile.ADVANCED);
            }

            View.OnClickListener applyAction = v -> {
                ProfileManager.AppProfile selected = current;
                if (rbProfilePowerSaver != null && rbProfilePowerSaver.isChecked()) {
                    selected = ProfileManager.AppProfile.POWERSAVER;
                } else if (rbProfileAdvanced != null && rbProfileAdvanced.isChecked()) {
                    selected = ProfileManager.AppProfile.ADVANCED;
                }
                pm.setCurrent(selected);
                Toast.makeText(ctx, "Profile: " + selected, Toast.LENGTH_SHORT).show();
            };

            if (btnApplyProfile != null) {
                btnApplyProfile.setOnClickListener(applyAction);
            } else {
                // Fallback: clicking radios immediately applies (if no explicit Apply button)
                if (rbProfilePowerSaver != null) rbProfilePowerSaver.setOnClickListener(applyAction);
                if (rbProfileAdvanced != null)   rbProfileAdvanced.setOnClickListener(applyAction);
            }
        } catch (Throwable t) {
            FileLog.getInstance().error(TAG, "setupProfileSection failed", t);
        }
    }

    private void populateUiWithCurrentConfigs() {
        // Proximity
        ProximityConfig proximityConfig = InfoPillProximityController.getProximityConfig();
        etEnterRadius.setText(String.valueOf(proximityConfig.enterRadiusM));
        etExitRadius.setText(String.valueOf(proximityConfig.exitRadiusM));
        etThrottleDrive.setText(String.valueOf(proximityConfig.stdThrottleDrivingMs));
        etThrottleFoot.setText(String.valueOf(proximityConfig.stdThrottleOnFootMs));

        if (mapFragment != null) {
            CameraFollowController controller = mapFragment.getCameraController();
            if (controller != null) {
                FollowConfig followConfig = controller.getFollowConfig();
                etFollowStdInterval.setText(String.valueOf(followConfig.stdIntervalMs));
                etFollowStdDist.setText(String.valueOf(followConfig.stdDistM));
                etFollowStdHeading.setText(String.valueOf(followConfig.stdHeadingDeg));
            }

            DeliveryFocusManager focusMgr = mapFragment.getFocusManager();
            if (focusMgr != null) {
                ZoomConfig zoomConfig = focusMgr.getZoomConfig();
                etZoomCloseMeters.setText(String.valueOf(zoomConfig.closeMeters));
                etZoomCloseZoom.setText(String.valueOf(zoomConfig.closeZoom));
                etZoomApproachMeters.setText(String.valueOf(zoomConfig.approachMeters));
                etZoomApproachZoom.setText(String.valueOf(zoomConfig.approachZoom));
            }
        }

        if (switchInsideBoost != null) {
            if (mapFragment != null) {
                switchInsideBoost.setChecked(mapFragment.isInsideZoneBoostEnabled());
                switchInsideBoost.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    mapFragment.setInsideZoneBoostEnabled(isChecked);
                    Toast.makeText(getContext(), "Inside boost " + (isChecked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
                });
            } else {
                switchInsideBoost.setEnabled(false);
            }
        }

        if (mapFragment == null) {
            FileLog.getInstance().debug(TAG, "Developer panel running without MapInnerFragment: Follow/Zoom sections limited");
        }
    }

    private void setupClickListeners(@NonNull View view) {
        view.findViewById(R.id.btn_reset_default).setOnClickListener(v -> {
           // mapFragment.initStrategyAndConfigForMediumLoad();
            populateUiWithCurrentConfigs(); // Refresh UI with default values
            Toast.makeText(getContext(), "Reset to default!", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btn_apply_proximity).setOnClickListener(v -> applyProximityConfig());
        view.findViewById(R.id.btn_apply_follow).setOnClickListener(v -> applyFollowConfig());
        view.findViewById(R.id.btn_apply_zoom).setOnClickListener(v -> applyZoomConfig());
    }

    private void applyProximityConfig() {
        try {
            ProximityConfig newConfig = new ProximityConfig();
            newConfig.enterRadiusM = Float.parseFloat(etEnterRadius.getText().toString());
            newConfig.exitRadiusM = Float.parseFloat(etExitRadius.getText().toString());
            newConfig.stdThrottleDrivingMs = Long.parseLong(etThrottleDrive.getText().toString());
            newConfig.stdThrottleOnFootMs = Long.parseLong(etThrottleFoot.getText().toString());

            InfoPillProximityController.applyProximityConfig(newConfig);
            FileLog.getInstance().debug(TAG, "Applied new ProximityConfig");
            Toast.makeText(getContext(), "Proximity Config Applied", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            handleApplyError(e);
        }
    }

    private void applyFollowConfig() {
        try {
            FollowConfig newConfig = new FollowConfig();
            newConfig.stdIntervalMs = Long.parseLong(etFollowStdInterval.getText().toString());
            newConfig.stdDistM = Float.parseFloat(etFollowStdDist.getText().toString());
            newConfig.stdHeadingDeg = Float.parseFloat(etFollowStdHeading.getText().toString());

            CameraFollowController controller = (mapFragment == null) ? null : mapFragment.getCameraController();
            if (controller == null) {
                Toast.makeText(getContext(), "Camera controller unavailable", Toast.LENGTH_SHORT).show();
                return;
            }
            controller.applyFollowConfig(newConfig);

            FileLog.getInstance().debug(TAG, "Applied new FollowConfig");
            Toast.makeText(getContext(), "Follow Config Applied", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            handleApplyError(e);
        }
    }

    private void applyZoomConfig() {
        try {
            ZoomConfig newConfig = new ZoomConfig();
            newConfig.closeMeters = Float.parseFloat(etZoomCloseMeters.getText().toString());
            newConfig.closeZoom = Float.parseFloat(etZoomCloseZoom.getText().toString());
            newConfig.approachMeters = Float.parseFloat(etZoomApproachMeters.getText().toString());
            newConfig.approachZoom = Float.parseFloat(etZoomApproachZoom.getText().toString());

            DeliveryFocusManager focusMgr = (mapFragment == null) ? null : mapFragment.getFocusManager();
            if (focusMgr == null) {
                Toast.makeText(getContext(), "Focus manager unavailable", Toast.LENGTH_SHORT).show();
                return;
            }
            focusMgr.applyZoomConfig(newConfig);

            FileLog.getInstance().debug(TAG, "Applied new ZoomConfig");
            Toast.makeText(getContext(), "Zoom Config Applied", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            handleApplyError(e);
        }
    }

    private void handleApplyError(Exception e) {
        FileLog.getInstance().error(TAG, "Failed to apply config", e);
        Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
}
