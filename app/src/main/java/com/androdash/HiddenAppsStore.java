package com.androdash;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class HiddenAppsStore {

    private static final String PREFS_NAME = "androdash_prefs";
    private static final String KEY_HIDDEN = "hidden_packages";

    private final SharedPreferences prefs;
    private final Set<String> hiddenPackages;

    public HiddenAppsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        hiddenPackages = new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<>()));
    }

    public void hideApp(String packageName) {
        hiddenPackages.add(packageName);
        save();
    }

    public void showApp(String packageName) {
        hiddenPackages.remove(packageName);
        save();
    }

    public boolean isHidden(String packageName) {
        return hiddenPackages.contains(packageName);
    }

    private void save() {
        prefs.edit().putStringSet(KEY_HIDDEN, new HashSet<>(hiddenPackages)).apply();
    }
}
