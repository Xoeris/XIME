# Implementation Plan - Fix Compilation Errors in :XIME.UI

This plan addresses several compilation errors and a Java version deprecation warning in the `:XIME.UI` module.

## Proposed Changes

### Build Configuration

#### [MODIFY] [build.gradle](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME.UI/build.gradle)
- Update `sourceCompatibility` and `targetCompatibility` to `JavaVersion.VERSION_17`.
- Add `kotlinOptions { jvmTarget = '17' }` (if Kotlin is used, though not currently seen, it's good practice).

### Haptic Feedback Fixes

#### [MODIFY] [SineReflectLayout.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME.UI/src/main/java/xime/ui/view/layout/SineReflectLayout.java)
- Add missing imports:
  - `xime.ui.view.utils.haptic.EngineHapticUtils`
  - `xime.ui.view.utils.haptic.PatternHapticUtils`
  - `xime.ui.view.utils.haptic.IntensityHapticUtils`
- Add `private EngineHapticUtils EngineHapticUtils;` field.
- Initialize `EngineHapticUtils` in the constructors.
- Correct references from `PatternHaptic` to `PatternHapticUtils` and `IntensityHaptic` to `IntensityHapticUtils`.
- Use `PatternHapticUtils.SINE_REFLECT` and `IntensityHapticUtils.SOFT` as intended.

### View Hierarchy Fixes

#### [MODIFY] [CircleProgressBar.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME.UI/src/main/java/xime/ui/view/widget/CircleProgressBar.java)
- Change `BaseParentNode` to `BaseParentEvent` in `requestDisallowIntercept` method.

#### [MODIFY] [LinearProgressBar.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME.UI/src/main/java/xime/ui/view/widget/LinearProgressBar.java)
- Change `BaseParentNode` to `BaseParentEvent` in `requestDisallowIntercept` method.

### UI Widget Fixes

#### [MODIFY] [FooterMenu.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME.UI/src/main/java/xime/ui/view/menu/FooterMenu.java)
- Add `private View miniChat;` field declaration.
- In `toggleMiniChat()`, initialize `miniChat` if it is null. Since `ai_chat_widget.xml` is a `<merge>` layout, it will be inflated into a new `GlassView` container.
- Add logic to handle toggling (removing from root if already added).

## Verification Plan

### Automated Tests
- Run `./gradlew :XIME.UI:assembleDebug` to verify that all compilation errors are resolved.

### Manual Verification
- None required as these are strictly compilation fixes.
