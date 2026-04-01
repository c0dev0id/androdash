package de.codevoid.androdash;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class AppHistoryStore {

    private static final String PREFS_NAME = "androdash_prefs";
    private static final String KEY_ENABLED = "app_history_enabled";
    private static final String KEY_PACKAGES = "app_history_packages";
    private static final int MAX_HISTORY = 5;
    private static final String SEPARATOR = ",";

    private final SharedPreferences prefs;

    public AppHistoryStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public void recordLaunch(String packageName) {
        List<String> packages = getRecentPackages();

        // Remove if already present, then prepend (MRU)
        packages.remove(packageName);
        packages.add(0, packageName);

        // Cap at MAX_HISTORY
        while (packages.size() > MAX_HISTORY) {
            packages.remove(packages.size() - 1);
        }

        prefs.edit().putString(KEY_PACKAGES, String.join(SEPARATOR, packages)).apply();
    }

    public List<String> getRecentPackages() {
        String raw = prefs.getString(KEY_PACKAGES, "");
        List<String> result = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String pkg : raw.split(SEPARATOR)) {
                if (!pkg.isEmpty()) {
                    result.add(pkg);
                }
            }
        }
        return result;
    }
}
