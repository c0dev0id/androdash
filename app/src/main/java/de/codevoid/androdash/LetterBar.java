package de.codevoid.androdash;

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
import android.view.ViewGroup;
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
    private static final char FOLDER_CHAR = '\u2302'; // ⌂

    private final Context context;
    private final LinearLayout container;
    private final ViewGroup scrollView;
    private List<AppModel> allApps;
    private final List<Character> selectedLetters = new ArrayList<>();
    private OnFilterChangedListener listener;
    private LetterSortStore letterSortStore;
    private MatchMethodStore matchMethodStore;
    private List<AppModel> lastFilteredApps;
    private int focusedAvailableIndex = -1; // -1 = no focus; index into available (non-selected) letters

    // Folder mode state
    private String activeFolderId = null;
    private List<AppModel> folderContents = null;

    public LetterBar(Context context, LinearLayout container, ViewGroup scrollView) {
        this.context = context;
        this.container = container;
        this.scrollView = scrollView;
    }

    public void setApps(List<AppModel> apps) {
        this.allApps = apps;
        selectedLetters.clear();
        focusedAvailableIndex = -1;
        activeFolderId = null;
        folderContents = null;
        updateButtons();
    }

    public void updateApps(List<AppModel> apps) {
        this.allApps = apps;
        updateButtons();
    }

    public void setOnFilterChangedListener(OnFilterChangedListener listener) {
        this.listener = listener;
    }

    public void setLetterSortStore(LetterSortStore store) {
        this.letterSortStore = store;
    }

    public void setMatchMethodStore(MatchMethodStore store) {
        this.matchMethodStore = store;
    }

    public boolean isConfigMode() {
        return selectedLetters.contains(GEAR_CHAR);
    }

    public boolean hasSelection() {
        return !selectedLetters.isEmpty();
    }

    public void enterConfigMode() {
        selectedLetters.clear();
        selectedLetters.add(GEAR_CHAR);
        updateButtons();
    }

    public void enterFolderMode(String folderId, List<AppModel> contents) {
        this.activeFolderId = folderId;
        this.folderContents = contents;
        selectedLetters.add(FOLDER_CHAR);
        focusedAvailableIndex = -1;
        updateButtons();
    }

    public boolean isFolderMode() {
        return activeFolderId != null;
    }

    public String getActiveFolderId() {
        return activeFolderId;
    }

    private void clearFolderState() {
        activeFolderId = null;
        folderContents = null;
    }

    public List<AppModel> getFilteredApps() {
        List<AppModel> sourceApps = (activeFolderId != null) ? folderContents : allApps;
        String prefix = getPrefix();
        if (prefix.isEmpty()) {
            lastFilteredApps = new ArrayList<>(sourceApps);
            return lastFilteredApps;
        }

        int method = matchMethodStore != null ? matchMethodStore.getMethod()
                : MatchMethodStore.METHOD_BEGINNING;

        List<AppModel> filtered = new ArrayList<>();
        for (AppModel app : sourceApps) {
            String upper = app.label.toUpperCase();
            if (method == MatchMethodStore.METHOD_BEGINNING) {
                if (upper.startsWith(prefix)) filtered.add(app);
            } else if (method == MatchMethodStore.METHOD_ANYWHERE) {
                if (upper.contains(prefix)) filtered.add(app);
            } else {
                if (fuzzyMatch(upper, prefix) >= 0) filtered.add(app);
            }
        }
        lastFilteredApps = filtered;
        return filtered;
    }

    /**
     * Returns the index in label after the last matched char of pattern,
     * or -1 if the pattern does not fuzzy-match.
     */
    private static int fuzzyMatch(String label, String pattern) {
        int li = 0;
        for (int pi = 0; pi < pattern.length(); pi++) {
            char target = pattern.charAt(pi);
            boolean found = false;
            while (li < label.length()) {
                if (label.charAt(li) == target) {
                    li++;
                    found = true;
                    break;
                }
                li++;
            }
            if (!found) return -1;
        }
        return li;
    }

    private String getPrefix() {
        StringBuilder sb = new StringBuilder();
        boolean afterFolder = (activeFolderId == null);
        for (Character c : selectedLetters) {
            if (c == GEAR_CHAR || c == FOLDER_CHAR) {
                if (c == FOLDER_CHAR) afterFolder = true;
                continue;
            }
            if (afterFolder) sb.append(c);
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
            int method = matchMethodStore != null ? matchMethodStore.getMethod()
                    : MatchMethodStore.METHOD_BEGINNING;

            Set<Character> nextChars = new LinkedHashSet<>();
            for (AppModel app : matching) {
                String upper = app.label.toUpperCase();
                if (method == MatchMethodStore.METHOD_BEGINNING) {
                    if (upper.length() > prefix.length()) {
                        nextChars.add(upper.charAt(prefix.length()));
                    }
                } else if (method == MatchMethodStore.METHOD_ANYWHERE) {
                    // Find all chars that can extend the substring match
                    String extended = prefix;
                    for (int i = 0; i < upper.length(); i++) {
                        if (upper.regionMatches(i, extended, 0, extended.length()) && i + extended.length() < upper.length()) {
                            nextChars.add(upper.charAt(i + extended.length()));
                        }
                    }
                } else {
                    // Fuzzy: find valid next chars after the current fuzzy match
                    int afterMatch = fuzzyMatch(upper, prefix);
                    if (afterMatch >= 0) {
                        for (int i = afterMatch; i < upper.length(); i++) {
                            nextChars.add(upper.charAt(i));
                        }
                    }
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
            transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
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

        // Apply focused background to the focused available letter button
        int availableCount = 0;
        for (int i = 0; i < container.getChildCount(); i++) {
            Button btn = (Button) container.getChildAt(i);
            String tag = (String) btn.getTag();
            if (tag != null && tag.startsWith("a:") && !tag.equals("a:" + GEAR_CHAR) && !tag.equals("a:" + FOLDER_CHAR)) {
                boolean isFocused = (availableCount == focusedAvailableIndex);
                if (isFocused) {
                    btn.setBackgroundResource(R.drawable.bg_button_focused);
                }
                availableCount++;
            }
        }

        // Notify listener — lastFilteredApps was set during computeTargetButtons()
        if (listener != null) {
            listener.onFilterChanged(lastFilteredApps != null ? lastFilteredApps : getFilteredApps());
        }

        // Force remeasure so scroll range reflects current content
        scrollView.requestLayout();

        // Scroll to show available letters
        scrollView.post(() -> {
            if (selectedLetters.isEmpty()) {
                scrollView.scrollTo(0, 0);
            }
        });
    }

    public boolean removeLastLetter() {
        if (selectedLetters.isEmpty()) return false;
        char removed = selectedLetters.remove(selectedLetters.size() - 1);
        if (removed == FOLDER_CHAR) {
            clearFolderState();
        }
        focusedAvailableIndex = -1;
        updateButtons();
        return true;
    }

    /** Returns the available (non-selected, non-gear, non-folder) letters from the current button list. */
    private List<Character> getAvailableLetters() {
        List<Character> result = new ArrayList<>();
        for (ButtonSpec spec : computeTargetButtons()) {
            if (!spec.selected && spec.letter != GEAR_CHAR && spec.letter != FOLDER_CHAR) {
                result.add(spec.letter);
            }
        }
        return result;
    }

    /**
     * Moves remote focus to the next available letter (wraps around).
     * Returns true if there were any available letters to focus.
     */
    public boolean focusNext() {
        List<Character> available = getAvailableLetters();
        if (available.isEmpty()) return false;
        if (focusedAvailableIndex < 0 || focusedAvailableIndex >= available.size() - 1) {
            focusedAvailableIndex = 0;
        } else {
            focusedAvailableIndex++;
        }
        updateButtons();
        return true;
    }

    /**
     * Moves remote focus to the previous available letter (wraps around).
     * Returns true if there were any available letters to focus.
     */
    public boolean focusPrev() {
        List<Character> available = getAvailableLetters();
        if (available.isEmpty()) return false;
        if (focusedAvailableIndex <= 0) {
            focusedAvailableIndex = available.size() - 1;
        } else {
            focusedAvailableIndex--;
        }
        updateButtons();
        return true;
    }

    /**
     * Selects the currently focused available letter.
     * Returns true if a letter was focused and selected.
     */
    public boolean selectFocused() {
        List<Character> available = getAvailableLetters();
        if (focusedAvailableIndex < 0 || focusedAvailableIndex >= available.size()) return false;
        char letter = available.get(focusedAvailableIndex);
        focusedAvailableIndex = -1;
        onAvailableLetterClick(letter);
        return true;
    }

    /**
     * Selects a letter by keyboard input, but only if that letter is currently
     * available in the letter bar. Returns true if the letter was selected.
     */
    public boolean selectLetter(char c) {
        char upper = Character.toUpperCase(c);
        List<ButtonSpec> available = computeTargetButtons();
        for (ButtonSpec spec : available) {
            if (!spec.selected && spec.letter == upper) {
                onAvailableLetterClick(upper);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the first app in the current filtered list, or null if none.
     */
    public AppModel getFirstFilteredApp() {
        List<AppModel> filtered = getFilteredApps();
        return filtered.isEmpty() ? null : filtered.get(0);
    }

    public void clearSelection() {
        selectedLetters.clear();
        focusedAvailableIndex = -1;
        clearFolderState();
        updateButtons();
    }

    private void onSelectedLetterClick(int index) {
        // Remove this letter and all after it
        while (selectedLetters.size() > index) {
            selectedLetters.remove(selectedLetters.size() - 1);
        }
        // If the folder char was removed, exit folder mode
        if (!selectedLetters.contains(FOLDER_CHAR)) {
            clearFolderState();
        }
        updateButtons();
    }

    private void onAvailableLetterClick(Character c) {
        if (letterSortStore != null && letterSortStore.isUsageSort() && c != GEAR_CHAR && c != FOLDER_CHAR) {
            letterSortStore.recordSelection(selectedLetters.size(), c);
        }
        selectedLetters.add(c);
        updateButtons();
    }

    private Button createLetterButton(char letter, boolean selected) {
        Button btn = new Button(context);
        btn.setText(String.valueOf(letter));
        btn.setAllCaps(letter != GEAR_CHAR && letter != FOLDER_CHAR);
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
        btn.setFocusableInTouchMode(true);

        int size = context.getResources().getDimensionPixelSize(R.dimen.letter_button_size);
        int margin = context.getResources().getDimensionPixelSize(R.dimen.letter_button_margin);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(margin, margin, margin, margin);
        btn.setLayoutParams(params);

        btn.setBackgroundResource(selected ? R.drawable.bg_button_selected : R.drawable.bg_button);

        return btn;
    }
}
