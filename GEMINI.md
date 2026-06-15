# Gemini CLI Context: EasyDelivery Android

This document provides foundational context, architecture overview, and development guidelines for the EasyDelivery Android project.

## Project Overview

**EasyDelivery** is a professional-grade Android application for package delivery drivers. It is designed with a **local-first** philosophy to ensure business continuity in environments with poor or no network connectivity.

### Core Value Propositions
- **Offline-First Execution**: Reliable local data persistence and deferred synchronization.
- **Delivery Efficiency**: Optimized workflows for parcel navigation, camera automation, and proximity-based cards.
- **Error Prevention**: Location-aware validation and guarded operational steps.

### Key Technologies
- **Language**: Java (primary), with some Kotlin interop.
- **UI**: Android View System (XML) + Jetpack Compose.
- **Persistence**: Room Database (`MyDb`).
- **Networking**: Volley & OkHttp.
- **Location & Maps**: Google Maps SDK, Fused Location Provider.
- **Hardware**: CameraX & ML Kit for barcode scanning.
- **Architecture**: Modular, SPI-based (Service Provider Interface).

---

## Technical Architecture

The project follows a layered architecture with clear separation of concerns.

### 1. SPI Layer (`courierservice` module)
Defines the `ICourierService` interface. This allows the app to support multiple courier backends (e.g., UniUni, SampleCourier) without modifying the core business logic.
- **Key Interface**: `com.hf.courierservice.ICourierService`
- **Callback**: `com.hf.courierservice.IResponseCallBack`

### 2. Implementation Layer (`uniuniservice`, `samplecourier`, etc.)
Contains concrete implementations of `ICourierService`. These modules handle backend-specific API calls and data mapping.
- **Active Courier**: Configured in `app/src/main/assets/config.json`.
- **Factory**: `com.hf.easydelivery.api.CourierServiceFactory` dynamically loads the courier implementation.

### 3. Core Business Logic (`app` module - `core` package)
- **`ResourceMgr`**: Singleton that orchestrates global resources (DB, Config, CourierService, Event Publisher).
- **`PendingPackagesMgr`**: Manages the local-first delivery queue. Handles persistence, asynchronous upload with exponential backoff, and image cleanup.
- **`DeliveryinfoMgr`**: Manages parcel data and navigation state.

### 4. View Layer (`app` module - `view` package)
Uses a combination of Activities and Fragments.
- **`MainActivity`**: Container for the main delivery workflow fragments.
- **`ScanFragment`**: Handles barcode scanning via CameraX/ML Kit.
- **`CameraActivity`**: Specialized activity for high-performance delivery photo capture.

---

## Development Guidelines

### Building and Running
1. **Prerequisites**: Android Studio (Koala or newer), JDK 17+.
2. **Gradle**: Uses Gradle 8.14 (see `gradle-wrapper.properties`).
3. **Environment**:
   - Add `MAPS_API_KEY` to `local.properties`.
   - Ensure `google-services.json` is present if required by specific plugins (though not explicitly seen, it's common).
4. **Configuration**:
   - `app/src/main/assets/config.json`: Set `"courier": "uniuni"` (or other implementation).

### Coding Standards
- **Local-First**: Always save data to the local database before attempting network operations. Use `PendingPackagesMgr` for delivery uploads.
- **Concurrency**: 
  - Perform DB operations on the DB thread (`ResourceMgr.getInstance().getDbHandler()`).
  - Perform UI updates on the main thread (`ResourceMgr.getInstance().getMainHandler()`).
- **Resource Management**: Use `ResourceMgr` to access global components.
- **Logging**: Use `com.hf.courierservice.apihelper.FileLog` for persistent logging, which is critical for debugging field issues.

### Adding a New Courier Support
1. Create a new Android Library module (e.g., `app:newcourier`).
2. Implement `ICourierService` in `com.hf.newcourier.CourierService`.
3. Add the module as a dependency in `app/build.gradle`.
4. Update `app/src/main/assets/config.json` to use `"newcourier"`.

---

## Key Files Reference
- `app/src/main/java/com/hf/easydelivery/ResourceMgr.java`: The central hub.
- `app/src/main/java/com/hf/easydelivery/core/PendingPackagesMgr.java`: Offline-sync logic.
- `app/courierservice/src/main/java/com/hf/courierservice/ICourierService.java`: The backend interface.
- `app/src/main/assets/config.json`: App configuration.
- `app/src/main/java/com/hf/easydelivery/MyDb.java`: Room database definition.
