# Create New Android Libraries: XIME.Media and XIME.Performance

The goal is to create two new empty Android library modules, `XIME.Media` and `XIME.Performance`, within the project structure and register them in the Gradle settings.

## Proposed Changes

### New Libraries Location
Both libraries will be created as siblings to `XIME.UI` in the directory:
`E:\Home\Projects\Users\Acelbyte\Categories\Programmings\projects\Xoeris\libraries\Android\Elarion\XIME\`

---

### [NEW] XIME.Media

#### [NEW] [build.gradle](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.Media/build.gradle)
Configure the Android library with:
- Namespace: `xime.media`
- Compile SDK: 37
- Min SDK: 24
- Java 11 compatibility

#### [NEW] [AndroidManifest.xml](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.Media/src/main/AndroidManifest.xml)
Basic manifest file.

#### [NEW] Proguard Files
- `consumer-rules.pro` (empty)
- `proguard-rules.pro` (empty)

---

### [NEW] XIME.Performance

#### [NEW] [build.gradle](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.Performance/build.gradle)
Configure the Android library with:
- Namespace: `xime.performance`
- Compile SDK: 37
- Min SDK: 24
- Java 11 compatibility

#### [NEW] [AndroidManifest.xml](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.Performance/src/main/AndroidManifest.xml)
Basic manifest file.

#### [NEW] Proguard Files
- `consumer-rules.pro` (empty)
- `proguard-rules.pro` (empty)

---

### [MODIFY] [settings.gradle](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.UI/settings.gradle)
Include the new modules and specify their project directories relative to `XIME.UI`.

```gradle
include ':XIME.Media'
project(':XIME.Media').projectDir = file('../XIME.Media')
include ':XIME.Performance'
project(':XIME.Performance').projectDir = file('../XIME.Performance')
```

## Verification Plan

### Automated Tests
- Run `gradle sync` to ensure the project structure is correctly recognized.
- Run `./gradlew :XIME.Media:assembleDebug` and `./gradlew :XIME.Performance:assembleDebug` to verify they build successfully.

### Manual Verification
- Check that the modules appear in the Android Studio project view.
