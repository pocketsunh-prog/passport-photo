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
- **Save White Background** - Replace background with plain white and save as JPEG
- **Save No Background** - Remove background and save as PNG with transparency

## Technical Specifications

### System Requirements

| Requirement | Value |
|-------------|-------|
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 (Android 15) |
| Compile SDK | 35 |
| Kotlin | 2.0.21 |
| Java | 11 |
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.7.3 |

### Build Configuration

| Setting | Value |
|---------|-------|
| Application ID | `com.passportphoto.app` |
| Version Code | 1 |
| Version Name | 1.0 |
| Minify (Release) | Disabled |
| ProGuard | Enabled (basic rules) |
| Signing | RSA 2048-bit, SHA-256 |

### Permissions

| Permission | Purpose | Required |
|------------|---------|----------|
| `CAMERA` | Camera access for photo capture | Yes |
| `READ_MEDIA_IMAGES` | Read photos from gallery (Android 13+) | For upload |
| `READ_EXTERNAL_STORAGE` | Read photos (Android 12 and below) | For upload |
| `WRITE_EXTERNAL_STORAGE` | Save photos (Android 9 and below) | For save |

### Camera Specifications

| Feature | Specification |
|---------|---------------|
| Camera API | CameraX 1.4.1 |
| Preview Resolution | 1280 × 720 |
| Capture Resolution | 1920 × 2560 (max) |
| Capture Mode | `CAPTURE_MODE_MAXIMIZE_QUALITY` |
| Flash | Disabled |
| JPEG Quality | 92-95% |
| Face Detection | Google ML Kit 16.1.7 |
| Detection Mode | `PERFORMANCE_MODE_ACCURATE` |
| Min Face Size | 15% of image |

### Image Processing Pipeline

| Step | Description |
|------|-------------|
| 1. Load | Decode image from URI/File with EXIF correction |
| 2. Crop | Crop to 40:50 aspect ratio (centered, biased toward top) |
| 3. Resize | Scale to minimum 1200×1600 pixels |
| 4. Save | Compress as JPEG (quality 92%) or PNG (transparent) |

### Output Specifications

| Format | Use Case | Extension |
|--------|----------|-----------|
| JPEG | Standard photo with background | `.jpg` |
| PNG | Photo with transparent background | `.png` |

### File Size Limits

| Limit | Value |
|-------|-------|
| Max File Size | 5 MB |
| Min Resolution | 1200 × 1600 pixels |
| Aspect Ratio | 4:5 (width:height) |

### Architecture

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose with Material 3 |
| Camera | CameraX (Preview + ImageCapture + ImageAnalysis) |
| Face Detection | Google ML Kit Face Detection |
| Image Processing | Android Bitmap API with EXIF support |
| State Management | ViewModel + StateFlow |
| Navigation | Jetpack Navigation Compose |
| Permissions | Accompanist Permissions |
| Image Loading | Coil 2.7.0 |

### Project Structure

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

### Dependencies

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

# Output: app/build/outputs/outputs/apk/release/app-release.apk
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
```

## Download

A pre-built signed release APK is available: [`PassportPhoto-v2.3-release.apk`](./PassportPhoto-v2.3-release.apk) (48.9 MB)

## License

MIT License
