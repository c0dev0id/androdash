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

    public void setHidden(String packageName, boolean hidden) {
        if (hidden) hiddenPackages.add(packageName);
        else        hiddenPackages.remove(packageName);
        save();
    }

    public void setExcludedFromHistory(String packageName, boolean excluded) {
        if (excluded) historyExcludedPackages.add(packageName);
        else          historyExcludedPackages.remove(packageName);
        save();
    }

    public boolean isHidden(String packageName) {
        return hiddenPackages.contains(packageName);
    }

    public boolean isExcludedFromHistory(String packageName) {
        return historyExcludedPackages.contains(packageName);
    }

    private void save() {
        prefs.edit()
                .putStringSet(KEY_HIDDEN, new HashSet<>(hiddenPackages))
                .putStringSet(KEY_HISTORY_EXCLUDED, new HashSet<>(historyExcludedPackages))
                .apply();
    }
}
