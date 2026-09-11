#!/usr/bin/env python3
"""
Batch class-rename tool.

Replaces old->new class names across:
  - .xml  (dotted form:  xime.ui.action.ChatAction)
  - .java (dotted form)
  - .smali (both slash form  Lxime/ui/action/ChatAction;  and dotted form
            in comments/strings)
  - .jar  (patches the UTF8 constant-pool entries inside every .class file,
            slash form  xime/ui/action/ChatAction)
  - any other text file (.txt, .gradle, .pro, .cfg, .json, ...) as a generic
    fallback ("usages"), dotted form

Usage:
    python3 replace_classes.py /path/to/project
    python3 replace_classes.py /path/to/project --dry-run
"""

import argparse
import io
import os
import re
import struct
import sys
import zipfile

# ---------------------------------------------------------------------------
# Mapping table (old -> new), dotted Java form. Everything else (slash form,
# smali "L...;" form) is derived automatically from this table.
# ---------------------------------------------------------------------------
MAPPING = [
    ("xime.ui.action.ChatAction", "xime.ui.widget.AIChatWidget"),
    ("xime.ui.action.ControlCenterAction", "xime.ui.widget.systemui.ControlCenter"),
    ("xime.ui.action.ElevatorAction", "xime.ui.widget.utils.FooterElevator"),
    ("xime.ui.action.FloatingAction", "xime.ui.widget.menu.FloatingMenu"),
    ("xime.ui.action.FooterAction", "xime.ui.widget.menu.FooterMenu"),
    ("xime.ui.action.GlobalPlayerAction", "xime.ui.widget.media.audio.GlobalPlayerWidget"),
    ("xime.ui.action.HeaderAction", "xime.ui.widget.menu.HeaderMenu"),
    ("xime.ui.action.LyricsAction", "xime.ui.widget.media.LyricsWidget"),
    ("xime.ui.action.PlayerAction", "xime.ui.widget.media.audio.PlayerWidget"),
    ("xime.ui.action.SubHeaderAction", "xime.ui.widget.menu.SubHeaderMenu"),
    ("xime.ui.widget.menu.TitleMenu", "xime.ui.widget.menu.TitleMenu"),
    ("xime.ui.control.ControlCode", "xime.ui.widget.common.Dropdown"),
    ("xime.ui.control.FloatingActionButton", "xime.ui.widget.FloatingButton"),
    ("xime.ui.control.IconTriggerControl", "xime.ui.widget.common.IconButton"),
    ("xime.ui.control.IconTriggerControlView", "xime.ui.widget.common.AppCompatIconButton"),
    ("xime.ui.control.MarkControl", "xime.ui.widget.common.Checkbox"),
    ("xime.ui.control.MarkControlView", "xime.ui.widget.common.AppCompatCheckbox"),
    ("xime.ui.control.RadioControl", "xime.ui.widget.common.RadioButton"),
    ("xime.ui.control.SearchControl", "xime.ui.widget.SearchBar"),
    ("xime.ui.control.ToggleControl", "xime.ui.widget.common.Switch"),
    ("xime.ui.control.TriggerControl", "xime.ui.widget.common.Button"),
    ("xime.ui.trackbar.LinearTrackBar", "xime.ui.widget.LinearProgressBar"),
    ("xime.ui.trackbar.CircularTrackBar", "xime.ui.widget.CircleProgressBar"),
    ("xime.ui.element.OrbitCarouselElement", "xime.ui.item.OrbitCarouselItem"),
    ("xime.ui.foundation.AnchorFoundation", "xime.ui.layout.RelativeLayout"),
    ("xime.ui.foundation.BlurFoundation", "xime.ui.layout.BlurLayout"),
    ("xime.ui.foundation.CrystalFoundation", "xime.ui.layout.CrystalLayout"),
    ("xime.ui.foundation.CycleFoundation", "xime.ui.layout.CycleLayout"),
    ("xime.ui.foundation.EqualizerAdvancedFoundation", "xime.ui.layout.EqualizerAdvancedLayout"),
    ("xime.ui.foundation.EqualizerMinimalFoundation", "xime.ui.layout.EqualizerMinimalLayout"),
    ("xime.ui.foundation.EqualizerProfessionalFoundation", "xime.ui.layout.EqualizerProfessionalLayout"),
    ("xime.ui.foundation.FlexFoundation", "xime.ui.layout.ConstraintLayout"),
    ("xime.ui.foundation.Foundation", "xime.ui.layout.Layout"),
    ("xime.ui.foundation.LayerFoundation", "xime.ui.layout.LayerLayout"),
    ("xime.ui.foundation.GlassFoundation", "xime.ui.layout.GlassLayout"),
    ("xime.ui.foundation.GlobalSpectrumFoundation", "xime.ui.layout.GlobalSpectrumLayout"),
    ("xime.ui.foundation.NestedFlowFoundation", "xime.ui.layout.NestedFlowLayout"),
    ("xime.ui.foundation.NexusFoundation", "xime.ui.layout.CoordinatorLayout"),
    ("xime.ui.foundation.OrbitFoundation", "xime.ui.layout.OrbitLayout"),
    ("xime.ui.foundation.OverlayFoundation", "xime.ui.layout.OverlayLayout"),
    ("xime.ui.foundation.PagerFoundation", "xime.ui.layout.PagerLayout"),
    ("xime.ui.foundation.ParallelBlurFoundation", "xime.ui.layout.BlurLinearLayout"),
    ("xime.ui.foundation.ParallelFoundation", "xime.ui.layout.LinearLayout"),
    ("xime.ui.foundation.SineReflectFoundation", "xime.ui.layout.SineReflectLayout"),
    ("xime.ui.foundation.SpectrumFoundation", "xime.ui.layout.SpectrumLayout"),
    ("xime.ui.foundation.StackFoundation", "xime.ui.layout.StackLayout"),
    ("xime.ui.panel.BottomPanel", "xime.ui.widget.BottomDialog"),
    ("xime.ui.panel.EdgePanel", "xime.ui.widget.EdgeDialog"),
    ("xime.ui.panel.Panel", "xime.ui.widget.Dialog"),
    ("xime.ui.panel.SurfacePanel", "xime.ui.widget.SurfaceDialog"),
    ("xime.ui.base.CardBaseView", "xime.ui.view.AppCompatCardView"),
    ("xime.ui.base.CardBase", "xime.ui.view.CardView"),
    ("xime.ui.base.ChipBase", "xime.ui.view.Chip"),
    ("xime.ui.base.CodeBaseView", "xime.ui.view.AppCompatTextView"),
    ("xime.ui.base.CodeBase", "xime.ui.view.TextView"),
    ("xime.ui.base.GapBase", "xime.ui.view.Space"),
    ("xime.ui.base.GlassBase", "xime.ui.view.GlassView"),
    ("xime.ui.base.LyricsBase-IA", "xime.ui.view.LyricsView-IA"),
    ("xime.ui.base.LyricsBase", "xime.ui.view.LyricsView"),
    ("xime.ui.base.OrbitRowBase", "xime.ui.view.OrbitRowView"),
    ("xime.ui.base.OrbitScrollerBase", "xime.ui.view.OrbitScrollerView"),
    ("xime.ui.base.OrbitBase", "xime.ui.view.OrbitView"),
    ("xime.ui.base.PictureBase", "xime.ui.view.PictureView"),
    ("xime.ui.base.SpectrumBase", "xime.ui.view.SpectrumView"),
    ("xime.ui.base.VideoBase", "xime.ui.view.VideoView"),
    ("xime.ui.base.Base", "xime.ui.view.View"),
]

# Sort longest-old-string-first so e.g. "LyricsBase-IA" is matched/replaced
# before the shorter "LyricsBase" (which is a substring of it). This avoids
# the fragile ordering trap the shell version had.
MAPPING.sort(key=lambda p: len(p[0]), reverse=True)


def build_variants(mapping):
    """
    Build ordered (old, new) replacement pairs for:
      - dotted form:  a.b.C -> a.b.D
      - slash form:   a/b/C -> a/b/D          (smali, jar internal names)
      - smali L-form: La/b/C; -> La/b/D;      (redundant w/ slash form but
                                                 kept for clarity/explicitness)
    Returns a dict of variant-name -> list[(old,new)] both already sorted
    longest-first.
    """
    dotted = list(mapping)
    slashed = [(o.replace(".", "/"), n.replace(".", "/")) for o, n in mapping]
    return {"dotted": dotted, "slashed": slashed}


VARIANTS = build_variants(MAPPING)

TEXT_EXTENSIONS_DOTTED = {".xml", ".java", ".kt", ".gradle", ".pro", ".cfg",
                           ".json", ".txt", ".properties", ".md"}
SMALI_EXTENSIONS = {".smali"}
JAR_EXTENSIONS = {".jar"}


def replace_text(content, pairs):
    """Apply an ordered list of literal (old,new) replacements to a string."""
    changed = False
    for old, new in pairs:
        if old in content:
            content = content.replace(old, new)
            changed = True
    return content, changed


def process_text_file(path, dry_run):
    try:
        with open(path, "r", encoding="utf-8", errors="strict") as f:
            content = f.read()
    except (UnicodeDecodeError, OSError):
        return False

    ext = os.path.splitext(path)[1].lower()
    if ext in SMALI_EXTENSIONS:
        # Smali uses slash form inside L...; type descriptors, but may also
        # contain dotted strings inside string constants/comments, so try both.
        content, c1 = replace_text(content, VARIANTS["slashed"])
        content, c2 = replace_text(content, VARIANTS["dotted"])
        changed = c1 or c2
    else:
        content, changed = replace_text(content, VARIANTS["dotted"])

    if changed and not dry_run:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
    return changed


# ---------------------------------------------------------------------------
# .class file patching (inside .jar archives)
#
# Java .class constant pool entries are referenced only by *index*, never by
# byte offset, so we can safely resize UTF8 entries (change their length +
# bytes) in place without touching anything else in the file.
# ---------------------------------------------------------------------------

# tag -> number of bytes to skip after the 1-byte tag (None = special-cased)
FIXED_SIZE_TAGS = {
    3: 4,   # Integer
    4: 4,   # Float
    5: 8,   # Long   (counts as two constant pool slots)
    6: 8,   # Double (counts as two constant pool slots)
    7: 2,   # Class
    8: 2,   # String
    9: 4,   # Fieldref
    10: 4,  # Methodref
    11: 4,  # InterfaceMethodref
    12: 4,  # NameAndType
    15: 3,  # MethodHandle
    16: 2,  # MethodType
    17: 4,  # Dynamic
    18: 4,  # InvokeDynamic
    19: 2,  # Module
    20: 2,  # Package
}
DOUBLE_SLOT_TAGS = {5, 6}


def patch_class_bytes(data, pairs_slashed):
    """Replace matching UTF8 constant-pool strings in a .class file's bytes.
    pairs_slashed: list of (old_slash_form, new_slash_form)
    Returns (new_bytes, changed_bool). Leaves data untouched (and returns
    changed=False) if the file doesn't parse as expected."""
    try:
        if data[0:4] != b"\xca\xfe\xba\xbe":
            return data, False
        pool_count = struct.unpack(">H", data[8:10])[0]
        pos = 10
        out = bytearray(data[:10])
        i = 1
        changed = False
        while i < pool_count:
            tag = data[pos]
            out.append(tag)
            pos += 1
            if tag == 1:  # Utf8
                length = struct.unpack(">H", data[pos:pos + 2])[0]
                raw = data[pos + 2:pos + 2 + length]
                pos += 2 + length
                try:
                    s = raw.decode("utf-8")
                except UnicodeDecodeError:
                    out += struct.pack(">H", length) + raw
                    i += 1
                    continue
                new_s = s
                for old, new in pairs_slashed:
                    if old in new_s:
                        new_s = new_s.replace(old, new)
                if new_s != s:
                    changed = True
                new_raw = new_s.encode("utf-8")
                out += struct.pack(">H", len(new_raw)) + new_raw
            else:
                size = FIXED_SIZE_TAGS.get(tag)
                if size is None:
                    # Unknown tag layout - bail out safely, leave file as-is.
                    return data, False
                out += data[pos:pos + size]
                pos += size
                if tag in DOUBLE_SLOT_TAGS:
                    i += 1  # double/long take two constant-pool slots
            i += 1
        out += data[pos:]
        return (bytes(out), changed) if changed else (data, False)
    except (IndexError, struct.error):
        return data, False


def process_jar_file(path, dry_run):
    try:
        with zipfile.ZipFile(path, "r") as zin:
            entries = zin.infolist()
            new_data = {}
            any_changed = False
            for info in entries:
                raw = zin.read(info.filename)
                name = info.filename
                out_name = name
                if name.endswith(".class"):
                    patched, changed = patch_class_bytes(raw, VARIANTS["slashed"])
                    if changed:
                        any_changed = True
                    raw = patched
                # Also rename the .class file itself if its path matches an
                # old fully-qualified class name (slash form, no extension).
                base = name[:-6] if name.endswith(".class") else None
                if base is not None:
                    for old, new in VARIANTS["slashed"]:
                        if base == old:
                            out_name = new + ".class"
                            any_changed = True
                            break
                new_data[out_name] = raw
    except (zipfile.BadZipFile, OSError):
        return False

    if not any_changed:
        return False

    if not dry_run:
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zout:
            for name, raw in new_data.items():
                zout.writestr(name, raw)
        with open(path, "wb") as f:
            f.write(buf.getvalue())
    return True


# ---------------------------------------------------------------------------
# Physical relocation: moves .java/.kt/.smali files into new package
# directories and fixes their `package ...;` declaration + self-references
# to the simple class name (e.g. every bare "ChatAction" inside
# ChatAction.java itself, which the global FQCN text-replace pass can't
# catch since it only ever sees the fully-qualified form).
# ---------------------------------------------------------------------------

SOURCE_EXTS = {".java": True, ".kt": True}
SMALI_EXT = ".smali"


def _split_fqcn(fqcn):
    parts = fqcn.split(".")
    return parts[:-1], parts[-1]  # (package_parts, simple_name)


def relocate_java_kt(target_dir, dry_run):
    moved = 0
    for root, _, files in os.walk(target_dir):
        for fname in list(files):
            ext = os.path.splitext(fname)[1].lower()
            if ext not in SOURCE_EXTS:
                continue
            full_path = os.path.join(root, fname)
            rel_path = os.path.relpath(full_path, target_dir)
            rel_no_ext = rel_path[: -len(ext)]
            path_parts = rel_no_ext.split(os.sep)

            for old_fqcn, new_fqcn in MAPPING:
                old_parts = old_fqcn.split(".")
                if path_parts[-len(old_parts):] != old_parts:
                    continue
                # match found: e.g. path_parts = [src, main, java, xime, ui, action, ChatAction]
                prefix_parts = path_parts[: -len(old_parts)]
                new_parts = new_fqcn.split(".")
                new_rel_parts = prefix_parts + new_parts
                new_rel_path = os.sep.join(new_rel_parts) + ext
                new_full_path = os.path.join(target_dir, new_rel_path)

                old_pkg_parts, old_simple = _split_fqcn(old_fqcn)
                new_pkg_parts, new_simple = _split_fqcn(new_fqcn)
                old_pkg = ".".join(old_pkg_parts)
                new_pkg = ".".join(new_pkg_parts)

                try:
                    with open(full_path, "r", encoding="utf-8") as f:
                        content = f.read()
                except (UnicodeDecodeError, OSError):
                    break

                # fix package declaration
                content = re.sub(
                    r"(^|\n)(\s*package\s+)" + re.escape(old_pkg) + r"(\s*;)",
                    r"\1\2" + new_pkg + r"\3",
                    content,
                )
                # fix bare self-references to the simple class name
                # (constructors, self-type usages, javadoc @link, etc.)
                content = re.sub(r"\b" + re.escape(old_simple) + r"\b",
                                  new_simple, content)

                print(f"[move]  {rel_path}  ->  {new_rel_path}")
                if not dry_run:
                    os.makedirs(os.path.dirname(new_full_path), exist_ok=True)
                    with open(new_full_path, "w", encoding="utf-8") as f:
                        f.write(content)
                    os.remove(full_path)
                moved += 1
                break  # only one mapping should ever match a given file
    return moved


def relocate_smali(target_dir, dry_run):
    moved = 0
    for root, _, files in os.walk(target_dir):
        for fname in list(files):
            if not fname.endswith(SMALI_EXT):
                continue
            full_path = os.path.join(root, fname)
            rel_path = os.path.relpath(full_path, target_dir)
            rel_no_ext = rel_path[: -len(SMALI_EXT)]
            path_parts = rel_no_ext.split(os.sep)

            for old_fqcn, new_fqcn in MAPPING:
                old_parts = old_fqcn.split(".")
                if path_parts[-len(old_parts):] != old_parts:
                    continue
                prefix_parts = path_parts[: -len(old_parts)]
                new_parts = new_fqcn.split(".")
                new_rel_path = os.sep.join(prefix_parts + new_parts) + SMALI_EXT
                new_full_path = os.path.join(target_dir, new_rel_path)

                # content (descriptor lines like "Lxime/ui/action/ChatAction;")
                # is already fixed by process_text_file's slash-form pass;
                # here we only need to physically relocate the file.
                print(f"[move]  {rel_path}  ->  {new_rel_path}")
                if not dry_run:
                    with open(full_path, "r", encoding="utf-8", errors="ignore") as f:
                        content = f.read()
                    content, _ = replace_text(content, VARIANTS["slashed"])
                    os.makedirs(os.path.dirname(new_full_path), exist_ok=True)
                    with open(new_full_path, "w", encoding="utf-8") as f:
                        f.write(content)
                    os.remove(full_path)
                moved += 1
                break
    return moved


def remove_empty_dirs(target_dir):
    removed = 0
    for root, dirs, files in os.walk(target_dir, topdown=False):
        if root == target_dir:
            continue
        if not os.listdir(root):
            os.rmdir(root)
            removed += 1
    return removed


def walk_and_process(target_dir, dry_run):
    stats = {"text": 0, "smali": 0, "jar": 0}
    for root, _, files in os.walk(target_dir):
        for fname in files:
            path = os.path.join(root, fname)
            ext = os.path.splitext(fname)[1].lower()
            if ext in JAR_EXTENSIONS:
                if process_jar_file(path, dry_run):
                    stats["jar"] += 1
                    print(f"[jar]   patched: {path}")
            elif ext in SMALI_EXTENSIONS:
                if process_text_file(path, dry_run):
                    stats["smali"] += 1
                    print(f"[smali] patched: {path}")
            elif ext in TEXT_EXTENSIONS_DOTTED:
                if process_text_file(path, dry_run):
                    stats["text"] += 1
                    print(f"[text]  patched: {path}")
    return stats


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                      formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("target_dir", nargs="?", default=".",
                         help="Directory to recurse into (default: current dir)")
    parser.add_argument("--dry-run", action="store_true",
                         help="Report what would change without writing anything")
    args = parser.parse_args()

    if not os.path.isdir(args.target_dir):
        print(f"Not a directory: {args.target_dir}", file=sys.stderr)
        sys.exit(1)

    print(f"Starting batch replacement in {args.target_dir}"
          f"{' (dry run)' if args.dry_run else ''} ...")

    # 1) fix all references/usages/imports/xml wherever the FQCN appears
    stats = walk_and_process(args.target_dir, args.dry_run)

    # 2) physically move renamed .java/.kt/.smali files into their new
    #    package directories, fixing package decl + self-references
    print("Relocating source files to match new packages...")
    moved_java = relocate_java_kt(args.target_dir, args.dry_run)
    moved_smali = relocate_smali(args.target_dir, args.dry_run)

    # 3) clean up now-empty old package directories
    removed_dirs = 0
    if not args.dry_run:
        removed_dirs = remove_empty_dirs(args.target_dir)

    print(f"Done. Files changed - text: {stats['text']}, "
          f"smali (content): {stats['smali']}, jar: {stats['jar']}, "
          f"java/kt moved: {moved_java}, smali moved: {moved_smali}, "
          f"empty dirs removed: {removed_dirs}")


if __name__ == "__main__":
    main()