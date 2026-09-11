package xime.core.system.manager;

import android.Manifest;
import android.accounts.Account;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.core.content.ContextCompat;

public class AccountManager {

    private static final String PREFS_NAME = "account_prefs";
    private static final String KEY_USER_NAME = "user_name";
    
    private static String cachedDisplayName = null;

    public static void setUserName(Context context, String name) {
        if (context == null) return;
        cachedDisplayName = name;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_USER_NAME, name)
                .apply();
    }

    public static void clearUserName(Context context) {
        if (context == null) return;
        cachedDisplayName = null;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_USER_NAME)
                .apply();
    }

    public static String getUserDisplayName(Context context) {
        if (context == null) return "";
        if (cachedDisplayName != null) return cachedDisplayName;

        // 0. Try to get name from internal preferences (Set by user)
        String savedName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_USER_NAME, null);
        if (!TextUtils.isEmpty(savedName)) {
            cachedDisplayName = savedName;
            return savedName;
        }

        // 1. Try to get the Device Name (Fastest - uses Settings.Global)
        String deviceName = getDeviceName(context);
        if (!TextUtils.isEmpty(deviceName)) {
            // Basic sanitization to avoid generic "Android" or "Phone" names
            String lower = deviceName.toLowerCase();
            if (!lower.contains("phone") && !lower.contains("android") && !lower.contains("tablet")) {
                cachedDisplayName = deviceName;
                return deviceName;
            }
        }

        // 2. Try to get name from the User Profile (Contacts Contract)
        String profileName = getProfileName(context);
        if (!TextUtils.isEmpty(profileName)) {
            cachedDisplayName = profileName;
            return profileName;
        }

        // 3. Try to get Google Account name using AccountManager
        String googleAccountName = getGoogleAccountName(context);
        if (!TextUtils.isEmpty(googleAccountName)) {
            cachedDisplayName = googleAccountName;
            return googleAccountName;
        }

        // Final fallback to generic name if nothing found
        return "User";
    }

    private static String getProfileName(Context context) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
                    == PackageManager.PERMISSION_GRANTED) {
                
                String[] projection = new String[]{ContactsContract.Profile.DISPLAY_NAME};
                try (Cursor cursor = context.getContentResolver().query(
                        ContactsContract.Profile.CONTENT_URI,
                        projection,
                        null,
                        null,
                        null
                )) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(ContactsContract.Profile.DISPLAY_NAME);
                        if (index != -1) {
                            String name = cursor.getString(index);
                            if (!TextUtils.isEmpty(name)) {
                                return name.trim();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String getGoogleAccountName(Context context) {
        try {
            android.accounts.AccountManager manager = android.accounts.AccountManager.get(context);
            Account[] accounts = manager.getAccountsByType("com.google");
            if (accounts != null && accounts.length > 0) {
                for (Account account : accounts) {
                    if (!TextUtils.isEmpty(account.name) && account.name.contains("@")) {
                        String parsedName = parseNameFromEmail(account.name);
                        if (!TextUtils.isEmpty(parsedName)) {
                            return parsedName;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String parseNameFromEmail(String email) {
        try {
            int atIndex = email.indexOf('@');
            if (atIndex <= 0) return null;
            
            String namePart = email.substring(0, atIndex);
            namePart = namePart.replaceAll("[._-]", " ");
            
            String[] words = namePart.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    sb.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String getDeviceName(Context context) {
        try {
            String name = Settings.Global.getString(context.getContentResolver(), "device_name");
            if (!TextUtils.isEmpty(name)) return name.trim();

            name = Settings.System.getString(context.getContentResolver(), "device_name");
            if (!TextUtils.isEmpty(name)) return name.trim();

            name = Settings.Secure.getString(context.getContentResolver(), "bluetooth_name");
            if (!TextUtils.isEmpty(name)) return name.trim();
        } catch (Exception ignored) {}
        return null;
    }
}

