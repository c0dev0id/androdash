package com.androdash;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class BackupManager {

    private static final String PREFS_NAME = "androdash_prefs";
    private static final int BACKUP_VERSION = 1;

    private final Context context;
    private final SharedPreferences prefs;
    private final BookmarkStore bookmarkStore;
    private final FolderStore folderStore;

    public BackupManager(Context context, BookmarkStore bookmarkStore, FolderStore folderStore) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.bookmarkStore = bookmarkStore;
        this.folderStore = folderStore;
    }

    public JSONObject exportToJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", BACKUP_VERSION);
            root.put("timestamp", java.time.Instant.now().toString());

            // Settings
            JSONObject settings = new JSONObject();
            settings.put("letter_sort_usage", prefs.getBoolean("letter_sort_usage", false));
            settings.put("letterbar_position", prefs.getInt("letterbar_position", 0));
            settings.put("match_method", prefs.getInt("match_method", 0));
            settings.put("app_history_enabled", prefs.getBoolean("app_history_enabled", false));
            settings.put("app_history_packages", prefs.getString("app_history_packages", ""));

            // Hidden packages
            Set<String> hidden = prefs.getStringSet("hidden_packages", new HashSet<>());
            JSONArray hiddenArr = new JSONArray();
            for (String pkg : hidden) {
                hiddenArr.put(pkg);
            }
            settings.put("hidden_packages", hiddenArr);
            root.put("settings", settings);

            // Bookmarks
            JSONArray bookmarksArr = new JSONArray();
            for (BookmarkStore.BookmarkData b : bookmarkStore.getAll()) {
                JSONObject obj = new JSONObject();
                obj.put("id", b.id);
                obj.put("label", b.label);
                obj.put("url", b.url);
                obj.put("hasCustomIcon", b.hasCustomIcon);
                String iconBase64 = encodeIconFile(new File(context.getFilesDir(), "bookmark_icons/" + b.id + ".png"));
                if (iconBase64 != null) {
                    obj.put("iconBase64", iconBase64);
                }
                bookmarksArr.put(obj);
            }
            root.put("bookmarks", bookmarksArr);

            // Folders
            JSONArray foldersArr = new JSONArray();
            for (FolderStore.FolderData f : folderStore.getAll()) {
                JSONObject obj = new JSONObject();
                obj.put("id", f.id);
                obj.put("label", f.label);
                obj.put("hasCustomIcon", f.hasCustomIcon);
                String iconBase64 = encodeIconFile(new File(context.getFilesDir(), "folder_icons/" + f.id + ".png"));
                if (iconBase64 != null) {
                    obj.put("iconBase64", iconBase64);
                }
                foldersArr.put(obj);
            }
            root.put("folders", foldersArr);

            // Folder members
            String membersJson = prefs.getString("folder_members", "{}");
            root.put("folder_members", new JSONObject(membersJson));

            return root;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean importFromJson(JSONObject root) {
        try {
            int version = root.optInt("version", 0);
            if (version < 1) return false;

            Set<String> installedPackages = getInstalledPackages();
            SharedPreferences.Editor editor = prefs.edit();

            // Settings
            if (root.has("settings")) {
                JSONObject settings = root.getJSONObject("settings");

                if (settings.has("letter_sort_usage")) {
                    editor.putBoolean("letter_sort_usage", settings.getBoolean("letter_sort_usage"));
                }
                if (settings.has("letterbar_position")) {
                    editor.putInt("letterbar_position", settings.getInt("letterbar_position"));
                }
                if (settings.has("match_method")) {
                    editor.putInt("match_method", settings.getInt("match_method"));
                }
                if (settings.has("app_history_enabled")) {
                    editor.putBoolean("app_history_enabled", settings.getBoolean("app_history_enabled"));
                }
                if (settings.has("app_history_packages")) {
                    String historyRaw = settings.getString("app_history_packages");
                    String filtered = filterPackageList(historyRaw, installedPackages);
                    editor.putString("app_history_packages", filtered);
                }

                // Hidden packages - filter to installed only
                if (settings.has("hidden_packages")) {
                    JSONArray hiddenArr = settings.getJSONArray("hidden_packages");
                    Set<String> hiddenSet = new HashSet<>();
                    for (int i = 0; i < hiddenArr.length(); i++) {
                        String pkg = hiddenArr.getString(i);
                        if (installedPackages.contains(pkg)) {
                            hiddenSet.add(pkg);
                        }
                    }
                    editor.putStringSet("hidden_packages", hiddenSet);
                }
            }

            // Bookmarks
            if (root.has("bookmarks")) {
                JSONArray bookmarksArr = root.getJSONArray("bookmarks");
                for (int i = 0; i < bookmarksArr.length(); i++) {
                    JSONObject obj = bookmarksArr.getJSONObject(i);
                    String id = obj.getString("id");
                    boolean hasCustomIcon = obj.optBoolean("hasCustomIcon", false);
                    String iconBase64 = obj.optString("iconBase64", null);

                    // Decode and save icon first
                    boolean iconSaved = false;
                    if (iconBase64 != null && !iconBase64.isEmpty()) {
                        iconSaved = decodeAndSaveIcon(iconBase64, "bookmark", id);
                    }

                    BookmarkStore.BookmarkData bookmark = new BookmarkStore.BookmarkData(
                            id,
                            obj.getString("label"),
                            obj.getString("url"),
                            hasCustomIcon && iconSaved);
                    bookmarkStore.save(bookmark);
                }
            }

            // Folders
            if (root.has("folders")) {
                JSONArray foldersArr = root.getJSONArray("folders");
                for (int i = 0; i < foldersArr.length(); i++) {
                    JSONObject obj = foldersArr.getJSONObject(i);
                    String id = obj.getString("id");
                    boolean hasCustomIcon = obj.optBoolean("hasCustomIcon", false);
                    String iconBase64 = obj.optString("iconBase64", null);

                    boolean iconSaved = false;
                    if (iconBase64 != null && !iconBase64.isEmpty()) {
                        iconSaved = decodeAndSaveIcon(iconBase64, "folder", id);
                    }

                    FolderStore.FolderData folder = new FolderStore.FolderData(
                            id,
                            obj.getString("label"),
                            hasCustomIcon && iconSaved);
                    folderStore.save(folder);
                }
            }

            // Folder members - filter to installed apps, but always create the folder entry
            if (root.has("folder_members")) {
                JSONObject members = root.getJSONObject("folder_members");
                JSONObject filteredMembers = new JSONObject();
                Iterator<String> keys = members.keys();
                while (keys.hasNext()) {
                    String folderId = keys.next();
                    JSONArray pkgArr = members.getJSONArray(folderId);
                    JSONArray filteredArr = new JSONArray();
                    for (int i = 0; i < pkgArr.length(); i++) {
                        String pkg = pkgArr.getString(i);
                        if (installedPackages.contains(pkg)) {
                            filteredArr.put(pkg);
                        }
                    }
                    // Always include the folder entry, even if empty
                    filteredMembers.put(folderId, filteredArr);
                }
                editor.putString("folder_members", filteredMembers.toString());
            }

            editor.apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String encodeIconFile(File file) {
        if (!file.exists()) return null;
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return Base64.encodeToString(data, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean decodeAndSaveIcon(String base64, String type, String id) {
        try {
            byte[] data = Base64.decode(base64, Base64.NO_WRAP);
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap == null) return false;

            if ("bookmark".equals(type)) {
                bookmarkStore.saveIcon(id, bitmap);
            } else {
                folderStore.saveIcon(id, bitmap);
            }
            bitmap.recycle();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Set<String> getInstalledPackages() {
        Set<String> packages = new HashSet<>();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = context.getPackageManager().queryIntentActivities(mainIntent, 0);
        for (ResolveInfo ri : apps) {
            packages.add(ri.activityInfo.packageName);
        }
        return packages;
    }

    private String filterPackageList(String commaSeparated, Set<String> installedPackages) {
        if (commaSeparated == null || commaSeparated.isEmpty()) return "";
        String[] parts = commaSeparated.split(",");
        List<String> filtered = new ArrayList<>();
        for (String pkg : parts) {
            String trimmed = pkg.trim();
            if (!trimmed.isEmpty() && installedPackages.contains(trimmed)) {
                filtered.add(trimmed);
            }
        }
        return String.join(",", filtered);
    }
}
