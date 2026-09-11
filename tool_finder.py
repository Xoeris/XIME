#!/usr/bin/env python3
"""
tool_finder.py

Shared helper for build_framework_jar.py / unpack_jar.py.

If a required tool (d8, apksigner, apktool, baksmali, aapt2, ...) isn't on
PATH and isn't where ANDROID_HOME says it should be, this recursively scans
a set of likely root directories (script dir, cwd, ANDROID_HOME, common
tool install locations, and any --search-dir the user passes) looking for
the tool by filename, and caches the result so repeat runs are fast.

Can be used as a library:

    from tool_finder import find_tool

    apktool_cmd = find_tool("apktool", extra_search_dirs=[...])

Or run standalone to just report what it finds:

    python tool_finder.py
    python tool_finder.py --search-dir "D:\\Tools"
"""

import argparse
import json
import os
import shutil
import sys
from pathlib import Path

CACHE_FILE = Path(__file__).resolve().parent / ".tool_finder_cache.json"

# filename -> how to invoke it once found (jar needs `java -jar`, exe/bat run directly)
TOOL_CANDIDATES = {
    "apktool": {
        "names": ["apktool.bat", "apktool", "apktool.jar"],
        "jar_names": {"apktool.jar"},
    },
    "baksmali": {
        "names": ["baksmali.bat", "baksmali", "baksmali.jar"],
        "jar_names": {"baksmali.jar"},
    },
    "smali": {
        "names": ["smali.bat", "smali", "smali.jar"],
        "jar_names": {"smali.jar"},
    },
    "d8": {
        "names": ["d8.bat", "d8"],
        "jar_names": set(),
    },
    "apksigner": {
        "names": ["apksigner.bat", "apksigner"],
        "jar_names": set(),
    },
    "aapt2": {
        "names": ["aapt2.exe", "aapt2"],
        "jar_names": set(),
    },
    "zipalign": {
        "names": ["zipalign.exe", "zipalign"],
        "jar_names": set(),
    },
}

# Directories worth checking beyond ANDROID_HOME/PATH, in rough priority order.
DEFAULT_ROOTS_ENV = ["ANDROID_HOME", "ANDROID_SDK_ROOT"]
COMMON_WINDOWS_ROOTS = [
    r"C:\Android",
    r"C:\Tools",
    r"C:\tools",
    r"D:\Android",
    r"D:\Tools",
]
COMMON_USER_ROOTS = [
    "~/Android/Sdk",
    "~/AppData/Local/Android/Sdk",
    "~/scoop/apps",
    "~/tools",
    "~/.android",
]

MAX_SCAN_DEPTH = 8  # avoid crawling entire drives forever
SKIP_DIR_NAMES = {".git", "node_modules", "build", ".gradle", "__pycache__", ".idea"}


def _load_cache() -> dict:
    if CACHE_FILE.exists():
        try:
            return json.loads(CACHE_FILE.read_text())
        except Exception:
            return {}
    return {}


def _save_cache(cache: dict):
    try:
        CACHE_FILE.write_text(json.dumps(cache, indent=2))
    except Exception:
        pass


def _walk_limited(root: Path, max_depth: int):
    """os.walk but stops descending past max_depth and skips noisy dirs."""
    root = root.resolve()
    root_depth = len(root.parts)
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIR_NAMES]
        depth = len(Path(dirpath).parts) - root_depth
        if depth >= max_depth:
            dirnames[:] = []
        yield dirpath, dirnames, filenames


def _candidate_roots(extra_search_dirs=None) -> list[Path]:
    roots = []

    for env_var in DEFAULT_ROOTS_ENV:
        val = os.environ.get(env_var)
        if val:
            roots.append(Path(val))

    roots.append(Path(__file__).resolve().parent)
    roots.append(Path.cwd())

    for raw in COMMON_WINDOWS_ROOTS + COMMON_USER_ROOTS:
        p = Path(raw).expanduser()
        if p.exists():
            roots.append(p)

    if extra_search_dirs:
        for d in extra_search_dirs:
            p = Path(d).expanduser()
            if p.exists():
                roots.append(p)

    # de-dupe, preserve order
    seen = set()
    unique = []
    for r in roots:
        rp = r.resolve() if r.exists() else r
        if rp not in seen:
            seen.add(rp)
            unique.append(r)
    return unique


def _scan_for_names(root: Path, target_names: set[str], max_depth: int = MAX_SCAN_DEPTH) -> list[Path]:
    hits = []
    if not root.exists():
        return hits
    for dirpath, _dirnames, filenames in _walk_limited(root, max_depth):
        for fname in filenames:
            if fname in target_names:
                hits.append(Path(dirpath) / fname)
    return hits


def find_tool(tool_key: str, extra_search_dirs=None, use_cache: bool = True, verbose: bool = True) -> list[str] | None:
    """
    Returns a command prefix list to invoke the tool, e.g. ["apktool"] or
    ["java", "-jar", "C:/tools/apktool.jar"], or None if not found anywhere.
    """
    if tool_key not in TOOL_CANDIDATES:
        raise ValueError(f"Unknown tool key: {tool_key}")

    spec = TOOL_CANDIDATES[tool_key]
    names = spec["names"]
    jar_names = spec["jar_names"]

    # 1. PATH lookup first (fastest, most likely correct)
    for name in names:
        found = shutil.which(name)
        if found:
            if verbose:
                print(f"  [tool_finder] Found '{tool_key}' on PATH: {found}")
            return _wrap(found, jar_names)

    # 2. Cache lookup
    cache = _load_cache() if use_cache else {}
    cached_path = cache.get(tool_key)
    if cached_path and Path(cached_path).exists():
        if verbose:
            print(f"  [tool_finder] Found '{tool_key}' via cache: {cached_path}")
        return _wrap(cached_path, jar_names)

    # 3. Check ANDROID_HOME/build-tools/<latest>/ explicitly (common case)
    for env_var in DEFAULT_ROOTS_ENV:
        sdk = os.environ.get(env_var)
        if not sdk:
            continue
        build_tools_dir = Path(sdk) / "build-tools"
        if build_tools_dir.exists():
            versions = sorted(
                [d for d in build_tools_dir.iterdir() if d.is_dir()],
                key=lambda p: p.name,
                reverse=True,
            )
            for version_dir in versions:
                for name in names:
                    candidate = version_dir / name
                    if candidate.exists():
                        if verbose:
                            print(f"  [tool_finder] Found '{tool_key}' in build-tools {version_dir.name}: {candidate}")
                        _remember(cache, tool_key, candidate)
                        return _wrap(str(candidate), jar_names)

    # 4. Recursive scan across candidate roots
    if verbose:
        print(f"  [tool_finder] '{tool_key}' not on PATH or in build-tools — scanning subdirectories...")
    target_names = set(names)
    for root in _candidate_roots(extra_search_dirs):
        if verbose:
            print(f"    scanning: {root}")
        hits = _scan_for_names(root, target_names)
        if hits:
            best = _pick_best(hits, jar_names)
            if verbose:
                print(f"  [tool_finder] Found '{tool_key}' by scan: {best}")
            _remember(cache, tool_key, best)
            return _wrap(str(best), jar_names)

    if verbose:
        print(f"  [tool_finder] Could not locate '{tool_key}' anywhere. "
              f"Install it or pass its path explicitly.")
    return None


def _pick_best(hits: list[Path], jar_names: set[str]) -> Path:
    # Prefer non-jar executables/bats over jars (avoids needing `java -jar` glue),
    # and prefer shallower paths (less likely to be some nested backup/copy).
    non_jar = [h for h in hits if h.name not in jar_names]
    pool = non_jar if non_jar else hits
    return min(pool, key=lambda p: len(p.parts))


def _wrap(path_str: str, jar_names: set[str]) -> list[str]:
    p = Path(path_str)
    if p.name in jar_names or p.suffix == ".jar":
        return ["java", "-jar", str(p)]
    return [str(p)]


def _remember(cache: dict, tool_key: str, path: Path):
    cache[tool_key] = str(path)
    _save_cache(cache)


def find_android_sdk(extra_search_dirs=None, verbose: bool = True) -> Path | None:
    """Locate an Android SDK root if ANDROID_HOME/ANDROID_SDK_ROOT aren't set."""
    for env_var in DEFAULT_ROOTS_ENV:
        val = os.environ.get(env_var)
        if val and Path(val).exists():
            return Path(val)

    if verbose:
        print("  [tool_finder] ANDROID_HOME not set/valid — scanning for an SDK root "
              "(looking for a 'platform-tools' or 'build-tools' folder)...")

    markers = {"platform-tools", "build-tools"}
    for root in _candidate_roots(extra_search_dirs):
        if not root.exists():
            continue
        for dirpath, dirnames, _filenames in _walk_limited(root, MAX_SCAN_DEPTH):
            if markers & set(dirnames):
                sdk_root = Path(dirpath)
                if verbose:
                    print(f"  [tool_finder] Found probable SDK root: {sdk_root}")
                return sdk_root
    return None


def main():
    parser = argparse.ArgumentParser(description="Scan for required build tools (apktool, baksmali, d8, apksigner, aapt2)")
    parser.add_argument("--search-dir", action="append", default=[], help="Additional directory to scan (repeatable)")
    parser.add_argument("--no-cache", action="store_true", help="Ignore/skip the cache file")
    parser.add_argument("--clear-cache", action="store_true", help="Delete the cache file and exit")
    args = parser.parse_args()

    if args.clear_cache:
        if CACHE_FILE.exists():
            CACHE_FILE.unlink()
            print(f"Cleared cache: {CACHE_FILE}")
        else:
            print("No cache file to clear.")
        return

    print("============================================================")
    print(" Scanning for required tools")
    print("============================================================\n")

    sdk_root = find_android_sdk(extra_search_dirs=args.search_dir)
    print(f"\nAndroid SDK root: {sdk_root or 'NOT FOUND'}\n")

    results = {}
    for tool_key in TOOL_CANDIDATES:
        print(f"[{tool_key}]")
        cmd = find_tool(tool_key, extra_search_dirs=args.search_dir, use_cache=not args.no_cache)
        results[tool_key] = cmd
        print()

    print("============================================================")
    print(" Summary")
    print("============================================================")
    for tool_key, cmd in results.items():
        status = " ".join(cmd) if cmd else "NOT FOUND"
        print(f"  {tool_key:<12} {status}")

    missing = [k for k, v in results.items() if v is None]
    if missing:
        print(f"\n[WARN] Missing tools: {', '.join(missing)}")
        print("       Install them, or place them somewhere under a scanned root,")
        print("       or rerun with --search-dir pointing at where they live.")
        sys.exit(1)
    else:
        print("\nAll tools found.")


if __name__ == "__main__":
    main()
