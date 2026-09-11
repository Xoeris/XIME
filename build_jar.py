#!/usr/bin/env python3
"""
build_jar.py

Packs XIME.* Gradle module build outputs into a single xime.jar
matching the target structure:

    xime.jar
    ├── android/
    ├── com/
    ├── META-INF/
    ├── res/
    ├── classes.dex
    ├── classes2.dex
    ├── ...

Run this from the directory that contains your built module outputs
(e.g. the project root, or wherever your build/ folders live), OR pass
--project-root to point at it explicitly.

Requires:
    - Android SDK build-tools (for d8) — set ANDROID_HOME or pass --sdk
    - Python 3.8+

Usage:
    python build_jar.py
    python build_jar.py --modules XIME.Core XIME.Graphics XIME.UI
    python build_jar.py --min-api 21 --output xime.jar
    python build_jar.py --sign --keystore my.jks --ks-pass pass:foo

Module discovery:
    By default, modules are AUTO-DISCOVERED by scanning --project-root for
    any immediate subdirectory named "XIME.*" that contains a build.gradle
    (or build.gradle.kts). This means new modules (e.g. XIME.Persistence)
    are picked up automatically without editing this script — no fixed
    module list to maintain. Pass --modules to override with an explicit
    list instead.
"""

import argparse
import os
import shutil
import subprocess
import sys
import zipfile

try:
    from tool_finder import find_tool, find_android_sdk
except ImportError:
    find_tool = None
    find_android_sdk = None
from pathlib import Path

# Fallback list, only used if auto-discovery finds nothing and --modules
# isn't given. Kept as a safety net, not the primary source of truth.
DEFAULT_MODULES = [
    "XIME.Core",
    "XIME.Graphics",
    "XIME.UI",
    "XIME.Imaging",
    "XIME.Media",
    "XIME.Animation",
    "XIME.Haptic",
    "XIME.Performance",
    "XIME.Terminal",
    "XIME.Tools",
]

MANIFEST_CONTENT = "Manifest-Version: 1.0\nCreated-By: Xoeris Build\n"


def discover_modules(project_root: Path) -> list[str]:
    """
    Scans project_root's immediate subdirectories for anything named
    'XIME.*' that looks like a real Gradle module (has build.gradle or
    build.gradle.kts). Returns sorted module names, e.g.:
        ['XIME.Animation', 'XIME.Core', 'XIME.Graphics', ...]
    """
    found = []
    for entry in sorted(project_root.iterdir()):
        if not entry.is_dir():
            continue
        if not entry.name.startswith("XIME."):
            continue
        has_gradle = (entry / "build.gradle").exists() or (entry / "build.gradle.kts").exists()
        if has_gradle:
            found.append(entry.name)
        else:
            print(f"[WARN] Skipping '{entry.name}' — no build.gradle(.kts) found, doesn't look like a module")
    return found


def run(cmd, **kwargs):
    print(f"  $ {' '.join(str(c) for c in cmd)}")
    result = subprocess.run(cmd, **kwargs)
    if result.returncode != 0:
        print(f"[ERROR] Command failed with exit code {result.returncode}: {' '.join(str(c) for c in cmd)}")
        sys.exit(result.returncode)
    return result


def find_gradlew(project_root: Path) -> Path:
    candidates = [
        project_root / ("gradlew.bat" if os.name == "nt" else "gradlew"),
    ]
    for c in candidates:
        if c.exists():
            return c
    print("[ERROR] Could not find gradlew in project root:", project_root)
    sys.exit(1)


def gradle_assemble(project_root: Path, modules: list[str], variant: str):
    gradlew = find_gradlew(project_root)
    tasks = [f":{m}:assemble{variant}" for m in modules]
    print(f"[1/5] Building modules via Gradle ({variant})...")
    run([str(gradlew), *tasks], cwd=project_root)


def find_module_aar(project_root: Path, module: str, variant: str) -> Path:
    variant_lower = variant.lower()
    aar_dir = project_root / module / "build" / "outputs" / "aar"
    if not aar_dir.exists():
        print(f"[ERROR] No AAR output dir found for module {module}: {aar_dir}")
        sys.exit(1)
    candidates = list(aar_dir.glob(f"*{variant_lower}*.aar")) or list(aar_dir.glob("*.aar"))
    if not candidates:
        print(f"[ERROR] No .aar file found in {aar_dir}")
        sys.exit(1)
    return candidates[0]


def extract_classes_and_res(project_root: Path, module: str, variant: str, work_dir: Path):
    aar_path = find_module_aar(project_root, module, variant)
    extract_dir = work_dir / "aar_extracted" / module
    extract_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(aar_path) as z:
        z.extractall(extract_dir)

    classes_jar = extract_dir / "classes.jar"
    module_classes_dir = work_dir / "classes" / module
    if classes_jar.exists():
        module_classes_dir.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(classes_jar) as z:
            z.extractall(module_classes_dir)
    else:
        print(f"[WARN] No classes.jar found inside {aar_path.name} for module {module}")

    module_res_dir = extract_dir / "res"
    merged_res_dir = work_dir / "res"
    if module_res_dir.exists():
        merged_res_dir.mkdir(parents=True, exist_ok=True)
        for item in module_res_dir.rglob("*"):
            if item.is_file():
                rel = item.relative_to(module_res_dir)
                dest = merged_res_dir / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                if not dest.exists():
                    shutil.copy2(item, dest)
                # if it exists already, first module's copy wins; flag collisions
                elif dest.read_bytes() != item.read_bytes():
                    print(f"[WARN] Resource collision (kept first): {rel}")


def find_d8(sdk_path: Path | None, search_dirs: list[str] | None = None) -> list[str]:
    """Returns a command prefix (list) to invoke d8, e.g. ['C:/.../d8.bat']."""
    if sdk_path is not None:
        build_tools_dir = sdk_path / "build-tools"
        if build_tools_dir.exists():
            versions = sorted(
                [d for d in build_tools_dir.iterdir() if d.is_dir()],
                key=lambda p: p.name,
                reverse=True,
            )
            d8_name = "d8.bat" if os.name == "nt" else "d8"
            for version_dir in versions:
                candidate = version_dir / d8_name
                if candidate.exists():
                    print(f"  Using build-tools: {version_dir.name}")
                    return [str(candidate)]

    # ANDROID_HOME didn't have it (or wasn't set) — scan subdirectories.
    if find_tool is not None:
        print("[WARN] d8 not found under the given SDK path — scanning subdirectories...")
        cmd = find_tool("d8", extra_search_dirs=search_dirs)
        if cmd:
            return cmd

    print("[ERROR] Could not locate d8 anywhere (checked SDK build-tools and scanned subdirectories).")
    print("        Pass --sdk pointing at a valid Android SDK, or --search-dir pointing at")
    print("        wherever d8/build-tools lives on this machine.")
    sys.exit(1)


def run_d8(d8_cmd: list[str], classes_root: Path, dex_output_dir: Path, min_api: int):
    print("[3/5] Running d8 (auto multidex)...")
    dex_output_dir.mkdir(parents=True, exist_ok=True)

    class_files = list(classes_root.rglob("*.class"))
    if not class_files:
        print(f"[ERROR] No .class files found under {classes_root}")
        sys.exit(1)

    # d8 can choke on huge arg lists on some shells; write a file list instead.
    filelist_path = dex_output_dir / "d8_input_files.txt"
    with open(filelist_path, "w") as f:
        for cf in class_files:
            f.write(str(cf) + "\n")

    # Note: modern d8 (build-tools 31+) has no --multi-dex flag — it splits
    # into classesN.dex automatically whenever the 64K method limit is hit.
    cmd = [
        *d8_cmd,
        "--min-api", str(min_api),
        "--output", str(dex_output_dir),
        "@" + str(filelist_path),
    ]
    run(cmd)


def assemble_jar(work_dir: Path, dex_output_dir: Path, output_jar: Path):
    print("[4/5] Assembling xime.jar contents...")
    pack_dir = work_dir / "framework_pack"
    if pack_dir.exists():
        shutil.rmtree(pack_dir)
    pack_dir.mkdir(parents=True)

    # dex files
    for dex_file in sorted(dex_output_dir.glob("classes*.dex")):
        shutil.copy2(dex_file, pack_dir / dex_file.name)

    # merged res/
    merged_res_dir = work_dir / "res"
    if merged_res_dir.exists():
        shutil.copytree(merged_res_dir, pack_dir / "res")
    else:
        print("[WARN] No merged res/ directory found — skipping res/ in output")

    # META-INF/MANIFEST.MF
    meta_inf = pack_dir / "META-INF"
    meta_inf.mkdir(parents=True, exist_ok=True)
    (meta_inf / "MANIFEST.MF").write_text(MANIFEST_CONTENT)

    # android/ and com/ passthrough directories, if present anywhere in extracted AARs
    aar_extracted_root = work_dir / "aar_extracted"
    for top_level_name in ("android", "com"):
        found_any = False
        for module_dir in aar_extracted_root.glob("*"):
            candidate = module_dir / top_level_name
            if candidate.exists():
                found_any = True
                dest = pack_dir / top_level_name
                for item in candidate.rglob("*"):
                    if item.is_file():
                        rel = item.relative_to(candidate)
                        target = dest / rel
                        target.parent.mkdir(parents=True, exist_ok=True)
                        if not target.exists():
                            shutil.copy2(item, target)
        if not found_any:
            print(f"[INFO] No '{top_level_name}/' directory found in any module AAR — "
                  f"creating empty '{top_level_name}/' placeholder")
            (pack_dir / top_level_name).mkdir(parents=True, exist_ok=True)

    # zip it up as xime.jar
    if output_jar.exists():
        output_jar.unlink()
    with zipfile.ZipFile(output_jar, "w", zipfile.ZIP_DEFLATED) as zf:
        for item in pack_dir.rglob("*"):
            if item.is_file():
                zf.write(item, item.relative_to(pack_dir))

    print(f"  Wrote {output_jar} ({output_jar.stat().st_size:,} bytes)")


def find_apksigner(sdk_path: Path | None, search_dirs: list[str] | None = None) -> list[str]:
    if sdk_path is not None:
        build_tools_dir = sdk_path / "build-tools"
        if build_tools_dir.exists():
            versions = sorted([d for d in build_tools_dir.iterdir() if d.is_dir()], reverse=True)
            apksigner_name = "apksigner.bat" if os.name == "nt" else "apksigner"
            for version_dir in versions:
                candidate = version_dir / apksigner_name
                if candidate.exists():
                    return [str(candidate)]

    if find_tool is not None:
        print("[WARN] apksigner not found under the given SDK path — scanning subdirectories...")
        cmd = find_tool("apksigner", extra_search_dirs=search_dirs)
        if cmd:
            return cmd

    print("[ERROR] Could not locate apksigner anywhere.")
    sys.exit(1)


def sign_jar(output_jar: Path, keystore: str, ks_pass: str, sdk_path: Path | None, search_dirs: list[str] | None = None):
    print("[5/5] Signing jar with apksigner...")
    apksigner_cmd = find_apksigner(sdk_path, search_dirs)
    run([*apksigner_cmd, "sign", "--ks", keystore, "--ks-pass", ks_pass, str(output_jar)])


def main():
    parser = argparse.ArgumentParser(description="Pack XIME.* Gradle modules into xime.jar")
    parser.add_argument("--project-root", default=".", help="Path to project root containing gradlew and XIME.* module dirs (default: current dir)")
    parser.add_argument("--modules", nargs="+", default=None,
                         help="Explicit module names to include. If omitted, modules are AUTO-DISCOVERED "
                              "by scanning --project-root for XIME.* directories containing a build.gradle(.kts).")
    parser.add_argument("--variant", default="Release", help="Build variant, e.g. Release / Debug")
    parser.add_argument("--min-api", type=int, default=21, help="Minimum API level for d8 multidex")
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"),
                         help="Path to Android SDK (defaults to ANDROID_HOME / ANDROID_SDK_ROOT; auto-scanned if unset/missing tools)")
    parser.add_argument("--output", default="xime.jar", help="Output jar filename (default: xime.jar)")
    parser.add_argument("--work-dir", default="_xime_jar_build", help="Scratch/work directory")
    parser.add_argument("--skip-gradle", action="store_true", help="Skip the Gradle build step (use existing build/ outputs)")
    parser.add_argument("--sign", action="store_true", help="Sign the resulting jar with apksigner")
    parser.add_argument("--keystore", help="Path to keystore for signing (required if --sign)")
    parser.add_argument("--ks-pass", help="Keystore password, e.g. pass:yourpassword (required if --sign)")
    parser.add_argument("--search-dir", action="append", default=None,
                         help="Additional directory to recursively scan for tools (d8, apksigner, ...) if not found via ANDROID_HOME/PATH (repeatable)")
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    work_dir = Path(args.work_dir).resolve()
    output_jar = Path(args.output).resolve()

    if work_dir.exists():
        shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True)

    if args.modules:
        modules = args.modules
        print(f"[0/5] Using explicit modules: {modules}")
    else:
        print(f"[0/5] Auto-discovering XIME.* modules under: {project_root}")
        modules = discover_modules(project_root)
        if not modules:
            print("[WARN] Auto-discovery found no XIME.* module directories with build.gradle(.kts). "
                  "Falling back to built-in default list.")
            modules = DEFAULT_MODULES
        print(f"       Discovered {len(modules)} module(s): {modules}")

    sdk_path = Path(args.sdk) if args.sdk else None
    if sdk_path is None or not sdk_path.exists():
        if args.sdk:
            print(f"[WARN] --sdk path does not exist: {args.sdk} — attempting to auto-locate an SDK...")
        else:
            print("[WARN] ANDROID_HOME/ANDROID_SDK_ROOT not set — attempting to auto-locate an SDK...")
        if find_android_sdk is not None:
            sdk_path = find_android_sdk(extra_search_dirs=args.search_dir)
        if sdk_path is None:
            print("[INFO] No SDK root found. Will still try to locate individual tools (d8, apksigner) "
                  "directly via subdirectory scan.")

    if not args.skip_gradle:
        gradle_assemble(project_root, modules, args.variant)
    else:
        print("[1/5] Skipping Gradle build (--skip-gradle), using existing outputs...")

    print("[2/5] Extracting classes + res from module AARs...")
    for module in modules:
        print(f"  - {module}")
        extract_classes_and_res(project_root, module, args.variant, work_dir)

    d8_cmd = find_d8(sdk_path, search_dirs=args.search_dir)
    dex_output_dir = work_dir / "dex_output"
    classes_root = work_dir / "classes"
    run_d8(d8_cmd, classes_root, dex_output_dir, args.min_api)

    assemble_jar(work_dir, dex_output_dir, output_jar)

    if args.sign:
        if not args.keystore or not args.ks_pass:
            print("[ERROR] --sign requires --keystore and --ks-pass")
            sys.exit(1)
        sign_jar(output_jar, args.keystore, args.ks_pass, sdk_path, search_dirs=args.search_dir)

    print(f"\nDone. xime.jar built at: {output_jar}")


if __name__ == "__main__":
    main()
