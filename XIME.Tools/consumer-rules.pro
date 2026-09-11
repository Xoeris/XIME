# Consumer ProGuard rules for XIME.Tools.
# Keep public API so apps that shrink their own code can still call XimeTools.
-keep public class xime.tools.XimeTools { public *; }
-keep public class xime.tools.FsType { *; }
-keep public interface xime.tools.ProgressListener { *; }
-keep public class xime.tools.options.** { public *; }
-keep public class xime.tools.exception.** { public *; }
