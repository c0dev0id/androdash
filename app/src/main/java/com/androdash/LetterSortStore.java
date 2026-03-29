package com.androdash;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LetterSortStore {

    private static final String PREFS_NAME = "androdash_prefs";
    private static final String KEY_USAGE_SORT = "letter_sort_usage";
    private static final String KEY_STACK_PREFIX = "letter_stack_";

    private final SharedPreferences prefs;

    public LetterSortStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isUsageSort() {
        return prefs.getBoolean(KEY_USAGE_SORT, false);
    }

    public void setUsageSort(boolean enabled) {
        prefs.edit().putBoolean(KEY_USAGE_SORT, enabled).apply();
    }

    public void recordSelection(int position, char letter) {
        String key = KEY_STACK_PREFIX + position;
        String stack = prefs.getString(key, "");

        // Remove letter if already in stack, then prepend it (MRU)
        StringBuilder sb = new StringBuilder();
        sb.append(letter);
        for (int i = 0; i < stack.length(); i++) {
            if (stack.charAt(i) != letter) {
                sb.append(stack.charAt(i));
            }
        }

        prefs.edit().putString(key, sb.toString()).apply();
    }

    public List<Character> getSortedLetters(int position, List<Character> available) {
        String stack = prefs.getString(KEY_STACK_PREFIX + position, "");

        List<Character> result = new ArrayList<>();

        // First: stack letters that are available, in MRU order
        for (int i = 0; i < stack.length(); i++) {
            char c = stack.charAt(i);
            if (available.contains(c)) {
                result.add(c);
            }
        }

        // Second: remaining available letters, alphabetically
        List<Character> remaining = new ArrayList<>();
        for (Character c : available) {
            if (!result.contains(c)) {
                remaining.add(c);
            }
        }
        Collections.sort(remaining);
        result.addAll(remaining);

        return result;
    }
}
