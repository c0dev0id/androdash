package com.androdash;

import android.content.Intent;
import android.graphics.drawable.Drawable;

public class AppModel implements Comparable<AppModel> {
    public final String label;
    public final String packageName;
    public final Drawable icon;
    public final Intent launchIntent;
    public final boolean isBookmark;

    public AppModel(String label, String packageName, Drawable icon, Intent launchIntent) {
        this(label, packageName, icon, launchIntent, false);
    }

    public AppModel(String label, String packageName, Drawable icon, Intent launchIntent, boolean isBookmark) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
        this.launchIntent = launchIntent;
        this.isBookmark = isBookmark;
    }

    @Override
    public int compareTo(AppModel other) {
        return this.label.compareToIgnoreCase(other.label);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppModel)) return false;
        return packageName.equals(((AppModel) o).packageName);
    }

    @Override
    public int hashCode() {
        return packageName.hashCode();
    }
}
