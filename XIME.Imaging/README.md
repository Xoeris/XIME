# XIME.Imaging

A lightweight, dependency-free Glide replacement for XIME.* modules. Entry
point is `Prism`, styled to match Glide's chained API.

```java
// Glide:
Glide.with(context).load(url).into(imageView);

// Prism:
Prism.load(url).into(imageView);
```

## Wiring it in

1. Copy this folder to your project root as `XIME.Imaging/`.
2. Add it to `settings.gradle`:
   ```groovy
   include ':XIME.Imaging'
   ```
3. Add it as a dependency wherever `libs.glide` is currently used
   (e.g. `XIME.UI/build.gradle`, `XIME.Performance/build.gradle`,
   `XIME.Media/build.gradle`):
   ```groovy
   implementation project(':XIME.Imaging')
   ```
4. Replace call sites:
   ```java
   // before
   Glide.with(context).load(url).placeholder(ph).into(imageView);

   // after
   Prism.load(url).placeholder(ph).into(imageView);
   ```
   `Prism.load(...)` derives its `Context` from `imageView.getContext()`
   internally, so there's no `.with(context)` step.

## What it does

- **Memory cache**, `LruCache<String, Bitmap>` sized to 1/8 of `maxMemory()`.
- **Disk cache**, flat files under `context.getCacheDir()/prism/`, keyed by
  `SHA-1(url)`, capped at 100MB with LRU (last-modified) eviction.
- **Downsampled decode**, computes `inSampleSize` from the target
  `ImageView`'s measured bounds (or an explicit `.override(w, h)`), so large
  network images don't get decoded at full resolution just to be shown in a
  thumbnail, the classic RecyclerView OOM source.
- **RecyclerView-safe**, each `.into(imageView)` call stamps a fresh request
  id on the view's tag. If the view gets recycled and rebound to a different
  item before the async load finishes, the stale result is detected and
  silently dropped instead of flashing the wrong image.
- **Cross-fade**, enabled by default (`TransitionDrawable`, 150ms); disable
  with `.crossFade(false)`.
- **Transformations**, `CircleTransformation`, `RoundedCornersTransformation`,
  or implement `Transformation` yourself. Applied on the background thread
  before the memory-cache insert, and folded into the cache key so
  transformed/untransformed variants don't collide.

## What it deliberately doesn't do (yet)

- GIF/animated image support
- Bitmap pooling / `BitmapFactory.Options.inBitmap` reuse (Glide's biggest
  edge over a from-scratch loader, worth adding if profiling shows GC
  pressure from repeated large decodes)
- Request coalescing across two different `ImageView`s loading the same URL
  concurrently
- Custom `OkHttp`/`Cronet` transport (uses `HttpURLConnection` to stay
  dependency-free)

None of these are architectural blockers, the task queue, cache layers, and
target-tagging scheme are the same shape Glide/Coil use, so any of the above
can be slotted in later without changing the `Prism.load(url).into(view)`
call sites.

## Files

| File | Role |
|---|---|
| `Prism.java` | Facade + singleton engine (caches, executor, main-thread handler) |
| `PrismRequest.java` | Fluent builder (`placeholder`, `error`, `transform`, `override`, `into`) |
| `PrismLoadTask.java` | Background `Runnable`: disk → network → decode → transform → dispatch |
| `Downloader.java` | Minimal blocking `HttpURLConnection` fetch |
| `BitmapDecoder.java` | `inSampleSize` downsampled decode |
| `MemoryCache.java` | `LruCache` wrapper |
| `DiskCache.java` | Hashed-filename disk cache with size-capped eviction |
| `Transformation.java` | Post-decode transform contract |
| `CircleTransformation.java` / `RoundedCornersTransformation.java` | Built-in transforms |
| `CacheKeys.java` | url + transform + size → cache key |
