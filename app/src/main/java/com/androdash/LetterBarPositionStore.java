package com.androdash;

import android.content.Context;
import android.content.SharedPreferences;

public class LetterBarPositionStore {

    public static final int POSITION_TOP = 0;
    public static final int POSITION_BOTTOM = 1;
    public static final int POSITION_LEFT = 2;
    public static final int POSITION_RIGHT = 3;

    private static final String PREFS_NAME = "androdash_prefs";
    private static final String KEY_POSITION = "letterbar_position";
    private static final String[] ARROWS = {"↑", "↓", "←", "→"};

    private final SharedPreferences prefs;

    public LetterBarPositionStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getPosition() {
        return prefs.getInt(KEY_POSITION, POSITION_TOP);
    }

    public void setPosition(int position) {
        prefs.edit().putInt(KEY_POSITION, position).apply();
    }

    public int nextPosition() {
        int next = (getPosition() + 1) % 4;
        setPosition(next);
        return next;
    }

    public String getArrow(int position) {
        return ARROWS[position];
    }

    public boolean isVertical() {
        int pos = getPosition();
        return pos == POSITION_LEFT || pos == POSITION_RIGHT;
    }
}
