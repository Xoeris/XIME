package xime.terminal;

/*
 * Example usage of XIME.Terminal:
 *
 * TerminalView terminalView = findViewById(R.id.terminal_view);
 * ExtraKeysRow extraKeysRow = findViewById(R.id.extra_keys_row);
 *
 * ShellBootstrap.ensureShell(this, new ShellBootstrap.BootstrapCallback() {
 *     @Override
 *     public void onBootstrapComplete(String shellPath) {
 *         TerminalSession session = new TerminalSession(shellPath, null, getFilesDir().getAbsolutePath(), 24, 80, terminalView);
 *         terminalView.attachSession(session);
 *         extraKeysRow.attachSession(session);
 *     }
 *
 *     @Override
 *     public void onBootstrapFailed(String error) {
 *         // Handle error
 *     }
 * });
 */
public class IntegrationExample {}
