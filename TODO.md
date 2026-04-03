# TODO

Resuming a `/simplify` full-app review. The following fixes were identified but not yet applied:

## AppGridAdapter.java

- [ ] Extract dialog window configuration (background + 90% width) into a `configureDialogWindow(AlertDialog)` helper method. Appears at lines 610-616, 708-714, 920-926, 1000-1006, 1052-1058, 1135-1141, 1213-1219 (7 identical blocks).
- [ ] Cache the result of `createDefaultFolderIcon()` as a field — currently allocates a new Bitmap on every RecyclerView bind (lines 227, 261, 949, 1023).

## FolderStore.java

- [ ] Add an in-memory reverse map (package → folderId) to make `getFolderForPackage()` / `isInAnyFolder()` O(1). Currently deserializes all members from SharedPreferences on every call; used in the keystroke hot path (`refreshDisplayedApps()`).

## MainActivity.java

- [ ] Merge `mergeBookmarksIntoApps()` and `mergeFoldersIntoApps()` into a single method — they always run together, each doing a full pass over `allApps` plus a sort.
- [ ] Cache `appsByPackage` as a field; currently rebuilt as a `HashMap` on every `refreshDisplayedApps()` call (line 613). Rebuild only when `allApps` changes.
