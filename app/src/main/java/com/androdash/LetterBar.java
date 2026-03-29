package com.androdash;

import android.content.Context;
import android.graphics.Typeface;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    private static class ButtonSpec {
        final String tag;
        final char letter;
        final boolean selected;
        final int selectedIndex; // -1 for available buttons

        ButtonSpec(String tag, char letter, boolean selected, int selectedIndex) {
            this.tag = tag;
            this.letter = letter;
            this.selected = selected;
            this.selectedIndex = selectedIndex;
        }
    }

    private List<ButtonSpec> computeTargetButtons() {
        List<ButtonSpec> target = new ArrayList<>();
        boolean inConfig = isConfigMode();

        // Selected buttons
        for (int i = 0; i < selectedLetters.size(); i++) {
            char c = selectedLetters.get(i);
            target.add(new ButtonSpec("s:" + i + ":" + c, c, true, i));
        }

        if (!inConfig) {
            String prefix = getPrefix();
            List<AppModel> matching = getFilteredApps();

            Set<Character> nextChars = new LinkedHashSet<>();
            for (AppModel app : matching) {
                String upper = app.label.toUpperCase();
                if (upper.length() > prefix.length()) {
                    nextChars.add(upper.charAt(prefix.length()));
                }
            }

            List<Character> sorted = new ArrayList<>(nextChars);
            if (letterSortStore != null && letterSortStore.isUsageSort()) {
                sorted = letterSortStore.getSortedLetters(prefix.length(), sorted);
            } else {
                Collections.sort(sorted);
            }

            for (Character c : sorted) {
                target.add(new ButtonSpec("a:" + c, c, false, -1));
            }

            // Gear button
            target.add(new ButtonSpec("a:" + GEAR_CHAR, GEAR_CHAR, false, -1));
        }

        return target;
    }

    private void updateButtons() {
        List<ButtonSpec> target = computeTargetButtons();

        if (container.getChildCount() > 0) {
            // Animate: fade out removed, slide persisting, fade in new
            TransitionSet transition = new TransitionSet();
            transition.setOrdering(TransitionSet.ORDERING_SEQUENTIAL);
            transition.addTransition(new Fade(Fade.OUT).setDuration(150));
            transition.addTransition(new ChangeBounds().setDuration(200));
            transition.addTransition(new Fade(Fade.IN).setDuration(150));
            TransitionManager.beginDelayedTransition(container, transition);
        }

        // Map existing buttons by tag
        Map<String, Button> existingByTag = new LinkedHashMap<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            Button btn = (Button) container.getChildAt(i);
            String tag = (String) btn.getTag();
            if (tag != null) {
                existingByTag.put(tag, btn);
            }
        }

        // Determine target tags
        Set<String> targetTags = new HashSet<>();
        for (ButtonSpec spec : target) {
            targetTags.add(spec.tag);
        }

        // Remove buttons not in target (iterate backwards for stable indices)
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            View child = container.getChildAt(i);
            String tag = (String) child.getTag();
            if (tag == null || !targetTags.contains(tag)) {
                container.removeViewAt(i);
            }
        }

        // Add new buttons and ensure correct ordering
        for (int i = 0; i < target.size(); i++) {
            ButtonSpec spec = target.get(i);
            Button existing = existingByTag.get(spec.tag);

            if (existing != null) {
                // Persisting button — update appearance if needed
                existing.setBackgroundResource(
                        spec.selected ? R.drawable.bg_button_selected : R.drawable.bg_button);

                // Ensure it's at the correct position
                int currentPos = container.indexOfChild(existing);
                if (currentPos != i) {
                    container.removeView(existing);
                    container.addView(existing, Math.min(i, container.getChildCount()));
                }
            } else {
                // New button
                Button btn = createLetterButton(spec.letter, spec.selected);
                btn.setTag(spec.tag);
                if (spec.selected) {
                    final int index = spec.selectedIndex;
                    btn.setOnClickListener(v -> onSelectedLetterClick(index));
                } else {
                    final char letter = spec.letter;
                    btn.setOnClickListener(v -> onAvailableLetterClick(letter));
                }
                container.addView(btn, Math.min(i, container.getChildCount()));
            }
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
