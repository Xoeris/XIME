# Update UI classes to extend XIME base classes

The goal is to ensure all UI components in the `:XIME.UI` module extend XIME's own base classes instead of standard Android/AndroidX/Material classes. This allows for centralized handling of common features like theme support, haptics, and event propagation.

## Proposed Changes

### [New Base Classes]

#### [NEW] [ImageView.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/view/ImageView.java)
Create a XIME base class for ImageViews.

#### [NEW] [NestedScrollView.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/layout/NestedScrollView.java)
Create a XIME base class for NestedScrollView.

#### [NEW] [RecyclerView.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/layout/RecyclerView.java)
Create a XIME base class for RecyclerView.

---

### [Component: xime.ui.bar]

#### [MODIFY] [SearchBar.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/bar/SearchBar.java)
Update to extend `xime.ui.layout.LinearLayout` instead of `android.widget.LinearLayout`.

---

### [Component: xime.ui.common]

#### [MODIFY] [IconButton.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/common/IconButton.java)
Update to extend `xime.ui.view.ImageView` (new) instead of `androidx.appcompat.widget.AppCompatImageButton`.

---

### [Component: xime.ui.dialog]

#### [MODIFY] [Dialog.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/dialog/Dialog.java)
Update to extend `xime.ui.layout.Layout` instead of `android.widget.FrameLayout`.

---

### [Component: xime.ui.layout]

#### [MODIFY] [OrbitItemLayout.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/layout/OrbitItemLayout.java)
Update to extend `xime.ui.layout.Layout` instead of `android.widget.FrameLayout`. Also update internal `ImageView` to `xime.ui.view.ImageView`.

#### [MODIFY] [OverlayLayout.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/layout/OverlayLayout.java)
Update to extend `xime.ui.layout.Layout` instead of `android.widget.FrameLayout`.

#### [MODIFY] [FlowLayout.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/layout/FlowLayout.java)
Update to extend `xime.ui.layout.NestedScrollView` (new) instead of `androidx.core.widget.NestedScrollView`.

#### [MODIFY] [CycleLayout.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/layout/CycleLayout.java)
Update to extend `xime.ui.layout.RecyclerView` (new) instead of `androidx.recyclerview.widget.RecyclerView`.

---

### [Component: xime.ui.view]

#### [MODIFY] [PictureView.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/view/PictureView.java)
Update to extend `xime.ui.view.ImageView` (new) instead of `android.widget.ImageView`.

#### [MODIFY] [OrbitRow.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android-Libraries/XIME/XIME.UI/src/main/java/xime/ui/view/OrbitRow.java)
Update internal layouts to use XIME versions (e.g. `rootParallel` should use `xime.ui.layout.LinearLayout` fully, ensuring no `android.widget.LinearLayout` references where XIME versions are intended).

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors due to circular dependencies or missing imports.
- Run existing UI tests in `:XIME.UI` if available.

### Manual Verification
- Verify that components still render correctly in Layout Editor / Previews.
