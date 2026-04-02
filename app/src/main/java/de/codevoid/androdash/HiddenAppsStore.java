package de.codevoid.androdash;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class HiddenAppsStore {

    private static final String PREFS_NAME = "androdash_prefs";
    private static final String KEY_HIDDEN = "hidden_packages";
    private static final String KEY_HISTORY_EXCLUDED = "history_excluded_packages";

    private final SharedPreferences prefs;
    private final Set<String> hiddenPackages;
    private final Set<String> historyExcludedPackages;

    public HiddenAppsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        hiddenPackages = new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<>()));
        historyExcludedPackages = new HashSet<>(prefs.getStringSet(KEY_HISTORY_EXCLUDED, new HashSet<>()));
    }

    public void hideApp(String packageName) {
        hiddenPackages.add(packageName);
        historyExcludedPackages.remove(packageName);
        save();
        saveHistoryExclusions();
    }

    public void showApp(String packageName) {
        hiddenPackages.remove(packageName);
        historyExcludedPackages.remove(packageName);
        save();
        saveHistoryExclusions();
    }

    public void excludeFromHistory(String packageName) {
        hiddenPackages.remove(packageName);
        historyExcludedPackages.add(packageName);
        save();
        saveHistoryExclusions();
    }

    public boolean isHidden(String packageName) {
        return hiddenPackages.contains(packageName);
    }

    public boolean isExcludedFromHistory(String packageName) {
        return historyExcludedPackages.contains(packageName);
    }

    private void save() {
        prefs.edit().putStringSet(KEY_HIDDEN, new HashSet<>(hiddenPackages)).apply();
    }

    private void saveHistoryExclusions() {
        prefs.edit().putStringSet(KEY_HISTORY_EXCLUDED, new HashSet<>(historyExcludedPackages)).apply();
    }
}
