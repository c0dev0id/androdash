# TODO

## Pending Feature (discussed, not approved)

### Termux Command Buttons
Add configurable shortcut buttons that execute a shell command via Termux and show the output in a popup dialog.
- Store commands as JSON in SharedPreferences (follow BookmarkStore pattern)
- Use `com.termux.RUN_COMMAND` intent + `EXTRA_RESULT_DIRECTORY` for output capture (requires Termux:API)
- Add `com.termux.permission.RUN_COMMAND` to manifest
- Guard against Termux not being installed

## Pending Simplify Findings

### AppGridAdapter.java

- [ ] Extract dialog window configuration (background + 90% width) into a `configureDialogWindow(AlertDialog)` helper method. Appears at lines 610-616, 708-714, 920-926, 1000-1006, 1052-1058, 1135-1141, 1213-1219 (7 identical blocks).
- [ ] Cache the result of `createDefaultFolderIcon()` as a field — currently allocates a new Bitmap on every RecyclerView bind (lines 227, 261, 949, 1023).

### FolderStore.java

- [ ] Add an in-memory reverse map (package → folderId) to make `getFolderForPackage()` / `isInAnyFolder()` O(1). Currently deserializes all members from SharedPreferences on every call; used in the keystroke hot path (`refreshDisplayedApps()`).

### MainActivity.java

- [ ] Merge `mergeBookmarksIntoApps()` and `mergeFoldersIntoApps()` into a single method — they always run together, each doing a full pass over `allApps` plus a sort.
- [ ] Cache `appsByPackage` as a field; currently rebuilt as a `HashMap` on every `refreshDisplayedApps()` call (line 613). Rebuild only when `allApps` changes.
