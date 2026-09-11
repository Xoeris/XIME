#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <pty.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define TAG "XIME-PTY"

JNIEXPORT jintArray JNICALL
Java_xime_terminal_pty_TerminalNative_createSession(JNIEnv *env, jclass clazz,
                                                       jstring cmd, jobjectArray envp,
                                                       jstring cwd, jint rows, jint cols) {
    int ptm;
    struct winsize win = { .ws_row = rows, .ws_col = cols };

    const char *c_cmd = (*env)->GetStringUTFChars(env, cmd, NULL);
    const char *c_cwd = cwd ? (*env)->GetStringUTFChars(env, cwd, NULL) : NULL;

    pid_t pid = forkpty(&ptm, NULL, NULL, &win);

    if (pid < 0) {
        if (c_cmd) (*env)->ReleaseStringUTFChars(env, cmd, c_cmd);
        if (c_cwd) (*env)->ReleaseStringUTFChars(env, cwd, c_cwd);
        return NULL;
    }

    if (pid == 0) {
        // Child
        if (c_cwd) chdir(c_cwd);

        // Prepare environment if needed (omitted for brevity in this step, using parent env)

        execl(c_cmd, c_cmd, NULL);

        // If execl fails
        _exit(1);
    }

    // Parent
    if (c_cmd) (*env)->ReleaseStringUTFChars(env, cmd, c_cmd);
    if (c_cwd) (*env)->ReleaseStringUTFChars(env, cwd, c_cwd);

    jintArray result = (*env)->NewIntArray(env, 2);
    int values[2] = {ptm, pid};
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    return result;
}

JNIEXPORT jint JNICALL
Java_xime_terminal_pty_TerminalNative_read(JNIEnv *env, jclass clazz, jint fd, jbyteArray buffer) {
    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, NULL);
    jsize len = (*env)->GetArrayLength(env, buffer);

    ssize_t bytes_read = read(fd, buf, len);

    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);
    return (jint)bytes_read;
}

JNIEXPORT jint JNICALL
Java_xime_terminal_pty_TerminalNative_write(JNIEnv *env, jclass clazz, jint fd, jbyteArray buffer, jint offset, jint length) {
    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, NULL);

    ssize_t bytes_written = write(fd, buf + offset, length);

    (*env)->ReleaseByteArrayElements(env, buffer, buf, JNI_ABORT);
    return (jint)bytes_written;
}

JNIEXPORT void JNICALL
Java_xime_terminal_pty_TerminalNative_resize(JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols) {
    struct winsize win = { .ws_row = rows, .ws_col = cols };
    ioctl(fd, TIOCSWINSZ, &win);
}

JNIEXPORT jint JNICALL
Java_xime_terminal_pty_TerminalNative_waitFor(JNIEnv *env, jclass clazz, jint pid) {
    int status;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_xime_terminal_pty_TerminalNative_close(JNIEnv *env, jclass clazz, jint fd) {
    close(fd);
}
