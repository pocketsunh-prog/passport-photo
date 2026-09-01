# Passport Photo Android App

An Android app for capturing and uploading passport photos that comply with Immigration Department requirements. Features real-time camera guidance with face detection, automatic photo processing, and compliance validation.

## Features

### Real-Time Camera Capture
- Live camera preview with face detection (powered by ML Kit)
- Switch between **front and back camera** with a dedicated button
- Interactive oval guide overlay with color-coded compliance status
- Instant feedback: centering, distance, head tilt, glasses detection
- One-tap capture when all requirements are met

### Photo Upload & Validation
- Upload existing photos from gallery
- Automatic compliance checking against passport standards
- Detailed validation report with pass/fail indicators

### Automatic Photo Processing
- Crops to 40:50 aspect ratio (passport standard)
- Resizes to minimum 1200×1600 pixels
- Corrects EXIF orientation
- Optimized JPEG compression (max 5 MB)

### Save Options
- **Save to Gallery** - Save processed photo as JPEG
- **Save No Background** - Remove background and save as PNG with transparency

## Passport Requirements Enforced

| Requirement | Standard | Validation |
|-------------|----------|------------|
| Dimensions | 40 mm × 50 mm | Aspect ratio crop + resize |
| Head Size | 32–36 mm (chin to top) | Face bounding box analysis |
| Background | Plain white | Corner/edge color sampling |
| Resolution | ≥ 1200 × 1600 px | Pixel dimension check |
| File Format | JPEG, ≤ 5 MB | Format + size validation |
| Lighting | No shadows/glare | Brightness & contrast analysis |
| Pose | Frontal, neutral expression | Euler angle detection |
| Accessories | No glasses/hats | Eye landmark + reflection detection |

## Architecture

- **UI**: Jetpack Compose with Material 3
- **Camera**: CameraX (Preview + ImageCapture + ImageAnalysis)
- **Face Detection**: Google ML Kit Face Detection
- **Image Processing**: Android Bitmap API with EXIF support
- **State Management**: ViewModel + StateFlow
- **Navigation**: Jetpack Navigation Compose
- **Permissions**: Accompanist Permissions

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- compileSdk 35, minSdk 24
- Kotlin 2.0.21
- A device or emulator with camera (API 24+)

## Building

### Debug
```bash
./gradlew assembleDebug
```

### Release

The release build is configured with signing. The keystore is located at `keystore/passport-photo.keystore`.

> **Security Note**: The default keystore password is set to `passport123` for development. For production releases, set environment variables instead:
> - `KEYSTORE_PASSWORD` — keystore password
> - `KEY_ALIAS` — key alias
> - `KEY_PASSWORD` — key password

```bash
# Build signed release APK
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```

### Generating a New Keystore

```bash
keytool -genkeypair -v \
  -keystore keystore/passport-photo.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias passport-photo \
  -storepass <your-password> \
  -keypass <your-password> \
  -dname "CN=Passport Photo, OU=Production, O=YourOrg, L=City, ST=State, C=US"
```

**Install on device:**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/release/app-release.apk

## Download

A pre-built signed release APK is available: [`PassportPhoto-v1.8-release.apk`](./PassportPhoto-v1.8-release.apk) (48.9 MB)

## Project Structure

```
app/src/main/java/com/passportphoto/app/
├── MainActivity.kt              # Entry point with Compose setup
├── camera/
│   ├── CameraManager.kt         # CameraX lifecycle + ML Kit analysis
│   ├── CameraViewModel.kt       # UI state and business logic
│   ├── ImageProcessor.kt        # Crop, resize, format conversion
│   └── PassportValidator.kt     # Compliance checking engine
└── ui/
    ├── PassportPhotoApp.kt      # Navigation graph
    ├── theme/                   # Color, Typography, Theme
    ├── components/
    │   └── FaceGuideOverlay.kt  # Real-time camera overlay
    └── screens/
        ├── HomeScreen.kt        # Take Photo / Upload Photo
        ├── CameraScreen.kt      # Camera with face guidance
        ├── GalleryPickerScreen.kt # Photo upload
        ├── PreviewScreen.kt     # Review + validation results
        └── PermissionGate.kt    # Runtime permission handling
```

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| CameraX | 1.4.1 | Camera preview, capture, analysis |
| ML Kit Face Detection | 16.1.7 | Real-time face detection & landmarks |
| Jetpack Compose BOM | 2024.12.01 | UI toolkit |
| Material 3 | 1.3.1 | Design system |
| Navigation Compose | 2.8.5 | Screen navigation |
| Coil | 2.7.0 | Image loading |
| Accompanist Permissions | 0.36.0 | Runtime permissions |
| EXIF Interface | 1.3.7 | Image orientation handling |

## License

MIT License
