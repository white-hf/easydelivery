# Map Empty-Screen Rescue PRD

## Background
During driving delivery, the current map follow experience is generally good. It can show nearby parcels based on the driver's route and motion direction.  
However, there is a real edge-case problem: sometimes there are no parcels inside the core visible area of the current map view, while the nearest parcel is actually just outside the screen. In that case, the driver often manually zooms or pans the map once to confirm where the next parcel is.

This is a low-frequency but real driving-assistance need. The goal is not to redesign the main driving map behavior, but to replace one manual map check with a lightweight system assist when appropriate.

## Product Goal
- When the normal driving map view contains no parcel in its core visible area, and the nearest parcel is only slightly outside the screen, the system provides one lightweight camera adjustment so the driver can quickly understand where that parcel is.
- After the adjustment, the system immediately returns to the existing motion-follow logic. It must not enter a persistent rescue mode.
- Android and iOS must behave consistently.

## Non-Goals
- Do not try to always show the next parcel during driving.
- Do not keep the map zoomed out for a long period.
- Do not change the normal driving cluster policy.
- Do not actively move the camera in high-speed cruising, far-distance candidates, or multi-target conflict scenarios.
- Do not replace the current main follow logic. This is only a one-time assist.

## Selected Solution
This requirement adopts `Option 3: one-time camera move toward the nearby off-screen parcel`.

The other two options are intentionally not selected:
- `Persistent rescue state machine`
  - Behavior is too complex and likely to create repeated camera adjustments.
  - GPS jitter, target switching, and edge in/out transitions would create unpredictable UX.
- `Slight zoom-out + temporary de-clustering`
  - Requires simultaneous changes to camera and cluster behavior.
  - The driver may feel that the map semantics are inconsistent: sometimes zoomed, sometimes not; sometimes clustered, sometimes not.

## User Story
- As a driver, when my current driving map view shows no parcel but the nearest parcel is just ahead or slightly to the side, I want the system to perform one lightweight assist so I know where it is, instead of manually zooming or panning the map.

## Trigger Principles
The system should trigger the assist only when all conditions below are true:

1. The map is currently in driving follow mode.
2. There is no parcel inside the current core visible area.
3. There is a stable nearest parcel candidate.
4. That candidate is outside the current screen, but not far from the current screen boundary.
5. The user has not recently panned, zoomed, or manually resumed follow.
6. The current situation is not high-speed cruising.

## Interaction Definition

### 1. Core Visible Area
To avoid treating edge-touching parcels as fully visible, the map should define a smaller core visible area inside the real screen bounds.

Suggested rule:
- Inset each screen edge by 10% to 15%.
- Only when this core visible area contains zero parcels may the rescue be triggered.

### 2. Candidate Parcel
The candidate must not be selected by raw straight-line distance alone.

Priority:
- Current primary focus parcel first.
- If there is no active primary focus, select the nearest parcel that is more aligned with the current forward motion direction.
- A parcel that has already triggered rescue and later entered the screen should not trigger rescue again.

### 3. Camera Action
When triggered, the system performs one lightweight camera move:
- Keep current `bearing`, `tilt`, and main zoom semantics as much as possible.
- Prefer adjusting the camera target only. Avoid obvious zoom changes.
- The goal is to bring the candidate parcel into the front or side-front screen edge, not to jump the camera to the parcel center.

### 4. Post-Action Behavior
- The rescue action runs once, then the system immediately returns to the existing follow logic.
- It must not continue tracking that parcel in a special rescue mode.
- The rescue must not redefine future normal driving-follow camera behavior.

## Scenario Boundaries

### Scenarios That Should Trigger
- Slow driving.
- No parcel inside the current core visible area.
- The nearest parcel is just outside the screen, in a situation where the driver would likely do one manual map check.

### Scenarios That Must Not Trigger
- The nearest parcel is far away, for example around 2 km.
- There is already at least one parcel in the core visible area.
- High-speed cruising.
- Dense downtown or apartment areas where moving the camera would still mostly reveal clusters.
- The nearest candidate is behind the driver or clearly not a useful target for the current motion direction.
- The user has just manually interacted with the map.

## Main UX Risks and Guards

### Risk 1: The camera feels like it jumps by itself
Guard:
- Use one-time assist only, not persistent rescue.
- Require zero parcel in the core visible area.
- Do not trigger multiple times for the same parcel.

### Risk 2: Repeated triggering while passing a parcel
Typical trajectory:
- off-screen -> on-screen -> off-screen

Guard:
- Once a parcel has entered the screen after a rescue, do not trigger rescue for the same parcel again.
- If the driver is already moving away from that parcel, do not trigger again.

### Risk 3: The mathematically nearest parcel is not the best UX target
Guard:
- Candidate selection must not rely on distance alone.
- It should also consider current focus, forward direction, and whether the parcel has already been passed.

### Risk 4: Conflict between user gestures and system camera control
Guard:
- After a map gesture, suppress rescue for a short period.
- Do not resume automatic rescue until normal follow has clearly resumed.

## Implementation Scope

### Android
- Camera follow control
  - `app/src/main/java/com/hf/easydelivery/map/CameraFollowController.java`
- Map view and visibility checks
  - `app/src/main/java/com/hf/easydelivery/map/MapInnerFragment.java`

### iOS
- Camera follow control
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/CameraFollowController.swift`
- Map view and visibility checks
  - `/Users/whitetang/Desktop/Code/easydelivery_v2/easydelivery_v2/Map/MapViewController.swift`

## Suggested Delivery Phases

### Phase 1
- Add a reliable check for whether the core visible area contains any parcel.
- Add candidate selection for parcels that are just outside the screen.
- Add one-time camera target adjustment.

### Phase 2
- Add duplicate-target prevention and passed-target filtering.
- Add logs and telemetry to observe real-world trigger frequency and driver feedback.

## Acceptance Criteria

1. During normal driving, if the core visible area already contains a parcel, camera behavior remains unchanged from the current version.
2. When the core visible area contains no parcel and the nearest parcel is just outside the screen, the system performs at most one lightweight assist to bring that parcel into view.
3. After the assist, the camera immediately returns to the current follow logic and does not keep tracking the rescue target.
4. In a pass-by sequence of `off-screen -> on-screen -> off-screen`, the same parcel must not repeatedly trigger rescue.
5. After the user manually interacts with the map, the system must not fight for camera control.
6. Android and iOS must keep the same trigger conditions, action semantics, and exit behavior.

## Telemetry and Logging Suggestions
- Log whether the current state matches `core visible area contains zero parcel`.
- Log candidate parcel id, relative position to screen boundary, and relation to current focus.
- Log whether rescue was triggered and whether the parcel was successfully brought into view.
- Log whether the user immediately adjusted the map manually after the rescue.

## Current Conclusion
This is a real but low-frequency assist need.  
The product direction must remain lightweight, one-time, and low-intrusion. It must not evolve back into persistent zoom-out logic or long-running camera control.
