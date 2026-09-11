# Walkthrough - Dynamic Compact Layout for `PlayerView`

I have implemented the logic to allow custom compact layouts for `PlayerView` using the `app:xoerisPlayerLayout` attribute. This also involved refactoring how the player content is initialized to support dynamic inflation.

## Changes Made

### 1. Attribute Definition
Added the `xoerisPlayerLayout` attribute to `attrs.xml` within the `PlayerView` `declare-styleable`.

### 2. Layout Refactoring
- **[player_widget.xml](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.UI/src/main/res/layout/player_widget.xml)**: Removed the hardcoded `<include>` for `playerContent`. This allows the layout to be injected dynamically at runtime.
- **[ids.xml](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.UI/src/main/res/values/ids.xml)**: Explicitly defined `playerContent` as an ID to ensure `R.id.playerContent` remains available for Java binding.

### 3. Component Logic & Attribute Refinement
- **[PlayerView.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.UI/src/main/java/xime/ui/view/PlayerView.java)**:
    - Added logic to read `app:xoerisPlayerLayout` and `app:xoerisPlayerExpandLayout`.
    - **Logical Attribute Handling**: The view now respects `app:xoerisBlurRadius` and `app:xoerisCornerRadius` from XML. Hardcoded defaults are only applied if these attributes are missing.
    - Added support for `app:xoerisPlayerSpectrumEnabled` and `app:xoerisPlayerSpectrumColor`.
    - Implemented dynamic inflation of the content into the `mainPager` at initialization.
    - Updated `applyLayoutState` to use the dynamic resource IDs for both compact and expanded states.

### 4. Layout Cleanup
- **[player_expand.xml](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.UI/src/main/res/layout/player_expand.xml)**: Removed a stray 'c' character at line 165 that could cause inflation errors.

### 4. Custom Pager Enhancement
- **[PagerLayout.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.UI/src/main/java/xime/ui/view/layout/PagerLayout.java)**: Added `addPage(View view, int index)` to allow inserting the dynamically inflated player content at the correct position (index 0) even after initial inflation.

### 5. Verification Layouts
Created the following in the `XIME.UI` module:
- **[player_view.xml](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.UI/src/main/res/layout/player_view.xml)**: Demonstrates usage of `app:xoerisPlayerLayout`.
- **[my_custom_compact_layout.xml](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.UI/src/main/res/layout/my_custom_compact_layout.xml)**: A sample custom compact layout.

## Verification Results

### Logic Check
- The default behavior remains unchanged: if `app:xoerisPlayerLayout` is not set, it defaults to `player_mini.xml` for initial display and `player_compact.xml` for morphing.
- If `app:xoerisPlayerLayout` is set, it overrides both, ensuring consistency in the compact state.
- Dynamic ID assignment to `R.id.playerContent` ensures all existing view bindings in `PlayerView.java` (like `trackTitle`, `albumArt`, etc.) continue to work as long as the custom layout provides those IDs.

### Build Stability
- Extended `PagerLayout` to support indexed insertion, which is critical for the `mainPager` structure in `PlayerView`.
- Verified that `R.id.playerContent` is explicitly defined to prevent compilation errors.

> [!TIP]
> When creating a custom layout for `app:xoerisPlayerLayout`, ensure you include standard IDs like `@id/playPauseButton`, `@id/albumArt`, and `@id/trackTitle` if you want the default media control logic to bind correctly.
