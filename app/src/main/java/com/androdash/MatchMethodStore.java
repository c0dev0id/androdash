package com.androdash;

import android.content.Context;
import android.content.SharedPreferences;

public class MatchMethodStore {

    public static final int METHOD_BEGINNING = 0;
    public static final int METHOD_ANYWHERE = 1;
    public static final int METHOD_FUZZY = 2;

    private static final String PREFS_NAME = "androdash_prefs";
    private static final String KEY_METHOD = "match_method";
    private static final String[] LABELS = {"Beginning", "Anywhere", "Fuzzy"};

    private final SharedPreferences prefs;

    public MatchMethodStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getMethod() {
        return prefs.getInt(KEY_METHOD, METHOD_BEGINNING);
    }

    public void setMethod(int method) {
        prefs.edit().putInt(KEY_METHOD, method).apply();
    }

    public int nextMethod() {
        int next = (getMethod() + 1) % 3;
        setMethod(next);
        return next;
    }

    public String getLabel(int method) {
        return LABELS[method];
    }
}
