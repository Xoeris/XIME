# Remove Delay in PlayerView Expansion

The goal is to eliminate the perceived delay and improve the smoothness when clicking or swiping to expand the `PlayerView` in the `musify-android` project (specifically in the `XIME.UI` library).

## User Review Required

> [!IMPORTANT]
> The changes involve optimizing the `XIME.UI` library, which is shared across multiple projects. The optimizations focus on caching `ConstraintSet` objects and managing blur updates during animations.

## Proposed Changes

### [XIME.UI](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.UI)

#### [MODIFY] [PlayerView.java](file:///E:/Home/Projects/Users/Acelbyte/Categories/Programmings/projects/Xoeris/libraries/Android/Elarion/XIME/XIME.UI/src/main/java/xime/ui/view/PlayerView.java)

1.  **Cache ConstraintSets**: Introduce member variables to cache `ConstraintSet` objects for `player_widget`, `player_compact`, and `player_expand` layouts. This avoids slow XML parsing during the transition.
2.  **Pause Blur Updates**: Implement `setPauseUpdates(true)` during the expansion animation to prevent `GlassLayout` from refreshing the blur every 16ms while the view is resizing.
3.  **Adjust Transition Parameters**: Reduce the transition duration from 400ms to 300ms for a more responsive feel.
4.  **Optimize Layout Application**: Update `applyLayoutState` to use the cached sets.

---

## Verification Plan

### Manual Verification
- **Click to Expand**: Tap the mini player and verify it expands instantly without a stutter or delay.
- **Swipe to Expand**: Swipe up on the mini player and verify the animation starts immediately and runs smoothly at 60fps (or device refresh rate).
- **Expansion Performance**: Observe if the UI thread is blocked (ANR or dropped frames) during expansion.
- **Blur Integrity**: Verify that the blur pauses during animation and resumes correctly once the player is fully expanded or collapsed.
