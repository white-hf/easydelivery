# Requirements Changelog

This file tracks product requirement changes that affect app behavior across versions.

## 2026-04-13

### Camera auto-complete after photo capture

- Status: Android implemented. iOS implemented in `/Users/whitetang/Desktop/Code/easydelivery_v2`.
- Scope:
  - Android delivery camera flow in `app/src/main/java/com/hf/easydelivery/view/CameraActivity.java`
  - iOS delivery camera flow in `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/View/PhotoViewController.swift`
- Requirement:
  - Standard delivery: after 2 photos are completed, start a 5-second countdown and auto-complete delivery if the driver does nothing.
  - Apartment delivery: require 3 photos. If a historical apartment exterior photo is auto-filled as the 3rd image, the same 5-second countdown starts automatically once the set is complete.
  - If same-address or nearby next-package candidates exist, reduce the countdown to 1 second so multi-package stops keep the original fast pace.
  - During the countdown, tapping the countdown UI cancels auto-complete and returns the page to manual confirmation mode.
  - After auto-complete is canceled, the driver can manually tap Done, delete photos, retake photos, or continue other existing manual actions.
  - Any photo-set change should re-enable auto-complete logic the next time the completion condition is met.
  - Auto-complete must reuse the same completion behavior as the existing manual Done action.

## 2026-04-19

### Camera auto-complete consistency fix

- Status: iOS fixed in `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/View/PhotoViewController.swift`.
- Fixes:
  - Restored `Done` to always complete immediately even while the auto-complete countdown is visible.
  - Kept tapping the countdown banner itself as the only in-banner action that cancels auto-complete and returns to manual mode.
  - Re-evaluate the active countdown when nearby/same-address next-package candidates finish refreshing, so multi-package stops can switch to the 1-second countdown instead of remaining on the default 5-second path.

### Scan unauthorized diagnostics

- Status: Android implemented.
- Scope:
  - `app/courierservice/src/main/java/com/hf/courierservice/apihelper/ApiRequestBase.java`
  - `app/courierservice/src/main/java/com/hf/courierservice/apihelper/exception/UnAuthorizedException.java`
  - `app/src/main/java/com/hf/easydelivery/component/BatchSubmitHelper.java`
  - `app/src/main/java/com/hf/easydelivery/view/model/ScanViewModel.java`
- Requirement:
  - When scan-related APIs fail with 401/403/449, logs must include the real HTTP status code and request URL.
  - Scan auto-submit logs must include the affected tracking number and scan batch id so repeated unauthorized failures can be traced to a specific record.

### Scan submit duplicate handling

- Status: Android implemented. iOS implemented in `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Core/OfflineSubmitManager.swift`.
- Scope:
  - `app/courierservice/src/main/java/com/hf/courierservice/apihelper/ApiRequestBase.java`
  - `app/courierservice/src/main/java/com/hf/courierservice/apihelper/exception/AlreadyScannedException.java`
  - `app/src/main/java/com/hf/easydelivery/component/BatchSubmitHelper.java`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Api/APIRequestBase.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Core/OfflineSubmitManager.swift`
- Requirement:
  - When scan submit returns `403` with `biz_code=SCAN.ALREADY.SCANNED`, treat it as an already-synced duplicate instead of login expiry.
  - Mark the local scan record uploaded, continue the remaining batch, and do not redirect the driver to login.

### Scan barcode and QR recognition improvements

- Status: Android implemented. iOS implemented in `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/View/ScanViewController.swift`.
- Scope:
  - `app/src/main/java/com/hf/easydelivery/view/ScanFragment.java`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/View/ScanViewController.swift`
- Requirement:
  - Improve recognition of screen-generated barcodes and QR codes in scan view.
  - Make hidden test mode actually affect scanner behavior instead of only showing a toast.
  - Prefer the largest and most centered detected code instead of blindly taking the first result.
  - Accept QR payloads that contain an embedded alphanumeric waybill token, not only payloads that are already a pure waybill string.
  - Use a more permissive size threshold and higher camera analysis quality in test mode.

### Scan view real-time feedback and visible screen-code mode

- Status: Android implemented. iOS implemented in `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/View/ScanViewController.swift`.
- Scope:
  - `app/src/main/java/com/hf/easydelivery/view/ScanFragment.java`
  - `app/src/main/res/layout/activity_scan.xml`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/View/ScanViewController.swift`
- Requirement:
  - Replace the hidden test-mode-only entrance with a visible in-view `screen code` toggle on both platforms.
  - Show a clear real-time scan status pill so drivers can tell whether the scanner is idle, has locked onto a candidate, saved a scan, or rejected the code.
  - Change the scan frame visual state with the status so detection feels responsive instead of static.

### Scan success feedback strengthening

- Status: Android implemented. iOS implemented in `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/View/ScanViewController.swift`.
- Scope:
  - `app/src/main/java/com/hf/easydelivery/view/ScanFragment.java`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/View/ScanViewController.swift`
- Requirement:
  - Make the top result card easier to confirm at a glance by increasing package and waybill font sizes.
  - Make the success flash shorter but stronger, with a brief green highlight on the result card.
  - Make success haptics more explicit so drivers can feel the scan confirmation without staring at the screen.

### Scan page visual hierarchy simplification

- Status: Android implemented. iOS implemented in `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/View/ScanViewController.swift`.
- Scope:
  - `app/src/main/res/layout/activity_scan.xml`
  - `app/src/main/java/com/hf/easydelivery/view/ScanFragment.java`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/View/ScanViewController.swift`
- Requirement:
  - Keep the scan page focused on three layers only: active scan area, most recent scan result, and scanned/unscanned counts with list access.
  - Reduce the visual weight of the counts row and list section so the camera area stays dominant.
  - Enlarge the visible scan area and widen the effective candidate region so drivers do not need to pin the barcode to the exact center.

### Address parsing reliability for house vs apartment

- Status: Android implemented. iOS implemented in `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Utils/AddressUtils.swift`.
- Scope:
  - `app/src/main/java/com/hf/easydelivery/common/Utils.java`
  - `app/src/main/java/com/hf/easydelivery/apartment/ApartmentAddressKeyBuilder.java`
  - `app/src/main/java/com/hf/easydelivery/dao/DeliveryInfo.java`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Utils/AddressUtils.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Utils/ApartmentAddressKeyBuilder.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Api/bean/DeliveryInfo.swift`
- Requirement:
  - Support Canadian postal codes with or without the embedded space so postal digits are not reused as apartment numbers.
  - Strip province, country, and postal-code tail tokens before unit inference so house addresses such as `98 King St, Dartmouth, NS, CA, B2Y 2S1` stay on the 2-photo path.
  - Only let confident unit sources trigger apartment behavior. Low-quality numeric guesses must no longer force the 3-photo apartment flow.
  - Keep explicit apartment formats such as `301-101 ...`, `Unit 301 ...`, `#301 ...`, and `117 Richmond St 409 ...` working on both platforms.

### Map empty-screen one-shot camera rescue

- Status: Android implemented. iOS implemented in `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapViewController.swift` and `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/CameraFollowController.swift`.
- Scope:
  - `app/src/main/java/com/hf/easydelivery/map/MapInnerFragment.java`
  - `app/src/main/java/com/hf/easydelivery/map/CameraFollowController.java`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapViewController.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/CameraFollowController.swift`
- Requirement:
  - Keep the existing driving follow and cluster behavior unchanged when the map core visible area already contains at least one parcel.
  - When the driver is moving slowly, the core visible area is empty, and the next parcel sits just outside the screen, perform a single light camera nudge to reveal that parcel instead of forcing the driver to manually pan or zoom.
  - Do not trigger this rescue while the driver is interacting with the map, while auto-follow is paused, at higher driving speeds, for far-away parcels, or repeatedly for the same parcel.

## 2026-05-14

### PowerSaver map browse mode iteration 1

- Status: Android implemented in progress. iOS implemented in progress.
- Scope:
  - `app/src/main/java/com/hf/easydelivery/map/MapDisplayMode.java`
  - `app/src/main/java/com/hf/easydelivery/map/MapDisplayModeResolver.java`
  - `app/src/main/java/com/hf/easydelivery/map/MapExperienceCoordinator.java`
  - `app/src/main/java/com/hf/easydelivery/map/PowerSaverBrowseParcelPresentationPolicy.java`
  - `app/src/main/java/com/hf/easydelivery/map/PowerSaverBrowseClusterPolicy.java`
  - `app/src/main/java/com/hf/easydelivery/map/MapInnerFragment.java`
  - `app/src/main/java/com/hf/easydelivery/map/MyClusterRenderer.java`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapDisplayMode.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapDisplayModeResolver.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapExperienceCoordinator.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/PowerSaverBrowseParcelPresentationPolicy.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/PowerSaverBrowseClusterPolicy.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapViewController.swift`
- Requirement:
  - Add a dedicated `PowerSaver browse` map display mode instead of reusing realtime follow semantics.
  - In `PowerSaver browse`, show a larger-area parcel map with clustering disabled by default.
  - Limit visible parcels to a bounded subset so dense areas stay readable even without cluster aggregation.
  - Keep the initial implementation strategy-based so future map modes, camera behavior, and marker presentation can evolve without adding more page-level condition branches.

### PowerSaver map browse mode iteration 2

- Status: Android implemented and verified. iOS implemented, build environment still needs follow-up validation.
- Scope:
  - `app/src/main/java/com/hf/easydelivery/map/MarkerColorTone.java`
  - `app/src/main/java/com/hf/easydelivery/map/MarkerStyleDecision.java`
  - `app/src/main/java/com/hf/easydelivery/map/MarkerStylePolicy.java`
  - `app/src/main/java/com/hf/easydelivery/map/DefaultMarkerStylePolicy.java`
  - `app/src/main/java/com/hf/easydelivery/map/PowerSaverBrowseMarkerStylePolicy.java`
  - `app/src/main/java/com/hf/easydelivery/map/MapExperience.java`
  - `app/src/main/java/com/hf/easydelivery/map/MapExperienceCoordinator.java`
  - `app/src/main/java/com/hf/easydelivery/map/PowerSaverBrowseParcelPresentationPolicy.java`
  - `app/src/main/java/com/hf/easydelivery/map/MyClusterRenderer.java`
  - `app/src/main/java/com/hf/easydelivery/map/MapInnerFragment.java`
  - `app/src/test/java/com/hf/easydelivery/map/PowerSaverBrowseMarkerStylePolicyTest.java`
  - `app/src/test/java/com/hf/easydelivery/map/PowerSaverBrowseParcelPresentationPolicyTest.java`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MarkerColorTone.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MarkerStyleDecision.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MarkerStylePolicy.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/DefaultMarkerStylePolicy.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/PowerSaverBrowseMarkerStylePolicy.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapExperience.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapExperienceCoordinator.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/PowerSaverBrowseParcelPresentationPolicy.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapViewController.swift`
- Requirement:
  - Add a dedicated `MarkerStylePolicy` layer so `PowerSaver browse` marker rendering is no longer hardcoded inside the map controller or renderer.
  - In `PowerSaver browse`, render lighter and smaller parcel markers while preserving clear cues for the current focus parcel and large parcels.
  - Prioritize the current primary parcel first, then same-stop parcels, then nearby parcels when selecting the visible browse subset.
  - Keep Android and iOS marker semantics aligned: same focus highlight intent, same large-parcel badge intent, and same browse-vs-follow visual weight split.

## 2026-05-17

### PowerSaver browse camera step 1

- Status: Android implemented and verified. iOS implemented, pending build validation in the local Apple toolchain environment.
- Scope:
  - `app/src/main/java/com/hf/easydelivery/map/DrivingCameraBehavior.java`
  - `app/src/main/java/com/hf/easydelivery/map/DefaultDrivingCameraBehavior.java`
  - `app/src/main/java/com/hf/easydelivery/map/PowerSaverBrowseDrivingCameraBehavior.java`
  - `app/src/main/java/com/hf/easydelivery/map/CameraFollowController.java`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/DrivingCameraBehavior.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/DefaultDrivingCameraBehavior.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/PowerSaverBrowseDrivingCameraBehavior.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/CameraFollowController.swift`
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapViewController.swift`
- Requirement:
  - Keep `PowerSaver browse` driving camera separate from realtime follow driving camera by introducing a dedicated driving camera behavior layer.
  - In `PowerSaver browse` while the driver is moving, stop using aggressive speed-band zoom and stop using parcel-approach zoom so the map remains a stable browse view.
  - Use a lower tilt and a restrained look-ahead target in `PowerSaver browse` driving mode so the visible road/building scale matches the browse-mode product goal more closely.
  - Preserve the existing low-speed or inside-stop centered behavior so drivers can still get a more local view only when they are actually slowing to stop and handle a nearby parcel.
