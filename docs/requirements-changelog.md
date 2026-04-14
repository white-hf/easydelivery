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
