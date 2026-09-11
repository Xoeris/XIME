package xime.ui.event;

public interface KeyEvent {

    boolean onKeyDown(int keyCode, android.view.KeyEvent event);

    boolean onKeyLongPress(int keyCode, android.view.KeyEvent event);

    boolean onKeyUp(int keyCode, android.view.KeyEvent event);

    boolean onKeyMultiple(int keyCode, int count, android.view.KeyEvent event);
}
