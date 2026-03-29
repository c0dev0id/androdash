package com.androdash;

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LetterBar {

    public interface OnFilterChangedListener {
        void onFilterChanged(List<AppModel> filteredApps);
    }

    private static final char GEAR_CHAR = '\u2699';

    private final Context context;
    private final LinearLayout container;
    private final HorizontalScrollView scrollView;
    private List<AppModel> allApps;
    private final List<Character> selectedLetters = new ArrayList<>();
    private OnFilterChangedListener listener;
    private LetterSortStore letterSortStore;

    public LetterBar(Context context, LinearLayout container, HorizontalScrollView scrollView) {
        this.context = context;
        this.container = container;
        this.scrollView = scrollView;

        LayoutTransition lt = new LayoutTransition();
        lt.enableTransitionType(LayoutTransition.CHANGING);
        container.setLayoutTransition(lt);
    }

    public void setApps(List<AppModel> apps) {
        this.allApps = apps;
        selectedLetters.clear();
        updateButtons();
    }

    public void setOnFilterChangedListener(OnFilterChangedListener listener) {
        this.listener = listener;
    }

    public void setLetterSortStore(LetterSortStore store) {
        this.letterSortStore = store;
    }

    public boolean isConfigMode() {
        return selectedLetters.contains(GEAR_CHAR);
    }

    public List<AppModel> getFilteredApps() {
        String prefix = getPrefix();
        if (prefix.isEmpty()) return new ArrayList<>(allApps);

        List<AppModel> filtered = new ArrayList<>();
        for (AppModel app : allApps) {
            if (app.label.toUpperCase().startsWith(prefix)) {
                filtered.add(app);
            }
        }
        return filtered;
    }

    private String getPrefix() {
        StringBuilder sb = new StringBuilder();
        for (Character c : selectedLetters) {
            if (c == GEAR_CHAR) continue;
            sb.append(c);
        }
        return sb.toString();
    }

    private void updateButtons() {
        if (container.getChildCount() > 0) {
            // Crossfade: same animation used for the app grid
            container.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                rebuildButtons();
                container.animate().alpha(1f).setDuration(150).start();
            }).start();
        } else {
            rebuildButtons();
        }
    }

    private void rebuildButtons() {
        container.removeAllViews();

        boolean inConfig = isConfigMode();

        // Add selected (fixed) letter buttons
        for (int i = 0; i < selectedLetters.size(); i++) {
            Button btn = createLetterButton(selectedLetters.get(i), true);
            final int index = i;
            btn.setOnClickListener(v -> onSelectedLetterClick(index));
            container.addView(btn);
        }

        if (!inConfig) {
            String prefix = getPrefix();
            List<AppModel> matching = getFilteredApps();

            // Collect unique next characters
            Set<Character> nextChars = new LinkedHashSet<>();
            for (AppModel app : matching) {
                String upper = app.label.toUpperCase();
                if (upper.length() > prefix.length()) {
                    nextChars.add(upper.charAt(prefix.length()));
                }
            }

            // Sort available letters
            List<Character> sorted = new ArrayList<>(nextChars);
            if (letterSortStore != null && letterSortStore.isUsageSort()) {
                sorted = letterSortStore.getSortedLetters(prefix.length(), sorted);
            } else {
                Collections.sort(sorted);
            }

            // Add available letter buttons
            for (Character c : sorted) {
                Button btn = createLetterButton(c, false);
                btn.setOnClickListener(v -> onAvailableLetterClick(c));
                container.addView(btn);
            }

            // Add gear button at the end (always available when not in config)
            Button gearBtn = createLetterButton(GEAR_CHAR, false);
            gearBtn.setOnClickListener(v -> onAvailableLetterClick(GEAR_CHAR));
            container.addView(gearBtn);
        }

        // Notify listener
        if (listener != null) {
            listener.onFilterChanged(getFilteredApps());
        }

        // Scroll to show available letters
        scrollView.post(() -> {
            if (selectedLetters.isEmpty()) {
                scrollView.scrollTo(0, 0);
            }
        });
    }

    public boolean removeLastLetter() {
        if (selectedLetters.isEmpty()) return false;
        selectedLetters.remove(selectedLetters.size() - 1);
        updateButtons();
        return true;
    }

    private void onSelectedLetterClick(int index) {
        // Remove this letter and all after it
        while (selectedLetters.size() > index) {
            selectedLetters.remove(selectedLetters.size() - 1);
        }
        updateButtons();
    }

    private void onAvailableLetterClick(Character c) {
        if (letterSortStore != null && letterSortStore.isUsageSort() && c != GEAR_CHAR) {
            letterSortStore.recordSelection(selectedLetters.size(), c);
        }
        selectedLetters.add(c);
        updateButtons();
    }

    private Button createLetterButton(char letter, boolean selected) {
        Button btn = new Button(context);
        btn.setText(String.valueOf(letter));
        btn.setAllCaps(letter != GEAR_CHAR);
        btn.setTextColor(context.getColor(R.color.text_primary));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 35);
        btn.setTypeface(btn.getTypeface(), Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setStateListAnimator(null); // Remove default elevation animation
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);
        btn.setPadding(0, 0, 0, 0);

        int size = context.getResources().getDimensionPixelSize(R.dimen.letter_button_size);
        int margin = context.getResources().getDimensionPixelSize(R.dimen.letter_button_margin);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(margin, margin, margin, margin);
        btn.setLayoutParams(params);

        btn.setBackgroundResource(selected ? R.drawable.bg_button_selected : R.drawable.bg_button);

        return btn;
    }
}
