# Android Project Build Documentation

This document describes the environment and dependencies required to compile and build the `easydelivery_Android` project from the command line on macOS without Android Studio.

## 1. System Environment Requirements

The project requires the following tools installed on the host machine:

- **Homebrew**: Package manager for macOS.
- **Java Development Kit (JDK)**: OpenJDK 17 is recommended.
- **Android SDK Command Line Tools**: For managing SDK platforms and build tools.

### Installation via Homebrew:
```bash
brew install openjdk@17
brew install --cask android-commandlinetools
```

## 2. Environment Variables

The following variables must be exported in your shell (or added to `~/.zshrc`):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
```

## 3. SDK Dependencies

The project targets specific Android SDK versions. Ensure these are installed using `sdkmanager`:

```bash
# Accept licenses first
yes | sdkmanager --licenses

# Install platforms and tools
sdkmanager "platforms;android-34" "platforms;android-36" "build-tools;35.0.0" "platform-tools"
```

## 4. Project Configuration

The project requires a `local.properties` file in the root directory with the following entries:

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
MAPS_API_KEY=your_google_maps_api_key_here
```
*Note: `MAPS_API_KEY` is required by the manifest merger. Use a placeholder value if the actual key is not available.*

## 5. Build Command

To compile the project and generate a Debug APK, run the Gradle wrapper from the project root:

```bash
chmod +x gradlew
./gradlew assembleDebug
```

## 6. Output Artifacts

Upon a successful build, the APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

## 7. Installation (ADB)

To install the generated APK to a connected device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
