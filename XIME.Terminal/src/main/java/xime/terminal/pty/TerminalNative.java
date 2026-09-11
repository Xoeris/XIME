package xime.terminal.pty;

public final class TerminalNative {
    static {
        System.loadLibrary("xime-terminal");
    }

    /**
     * Creates a new PTY session.
     * @return An integer array: [fd, pid]
     */
    public static native int[] createSession(String cmd, String[] env, String cwd, int rows, int cols);

    public static native int read(int fd, byte[] buffer);

    public static native int write(int fd, byte[] buffer, int offset, int length);

    public static native void resize(int fd, int rows, int cols);

    public static native int waitFor(int pid);

    public static native void close(int fd);
}
