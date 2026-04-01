package de.codevoid.androdash;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FolderStore {

    private static final String PREFS_NAME = "androdash_prefs";
    private static final String KEY_FOLDERS = "folders";
    private static final String KEY_MEMBERS = "folder_members";
    private static final String ICON_DIR = "folder_icons";
    private static final int ICON_SIZE = 96;

    private final SharedPreferences prefs;
    private final File iconDir;
    private final Resources resources;

    public static class FolderData {
        public String id;
        public String label;
        public boolean hasCustomIcon;

        public FolderData(String id, String label, boolean hasCustomIcon) {
            this.id = id;
            this.label = label;
            this.hasCustomIcon = hasCustomIcon;
        }

        public String getPackageName() {
            return "folder://" + id;
        }
    }

    public FolderStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        iconDir = new File(context.getFilesDir(), ICON_DIR);
        resources = context.getResources();
        if (!iconDir.exists()) {
            iconDir.mkdirs();
        }
    }

    // --- Folder CRUD ---

    public List<FolderData> getAll() {
        List<FolderData> result = new ArrayList<>();
        String json = prefs.getString(KEY_FOLDERS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                result.add(new FolderData(
                        obj.getString("id"),
                        obj.getString("label"),
                        obj.optBoolean("hasCustomIcon", false)));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public void save(FolderData folder) {
        List<FolderData> all = getAll();
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(folder.id)) {
                all.set(i, folder);
                found = true;
                break;
            }
        }
        if (!found) {
            all.add(folder);
        }
        persistFolders(all);
    }

    public void delete(String id) {
        List<FolderData> all = getAll();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).id.equals(id)) {
                all.remove(i);
            }
        }
        persistFolders(all);
        deleteIcon(id);

        // Remove membership entries for this folder
        Map<String, List<String>> members = getAllMembers();
        members.remove(id);
        persistMembers(members);
    }

    public FolderData getById(String id) {
        for (FolderData f : getAll()) {
            if (f.id.equals(id)) return f;
        }
        return null;
    }

    public boolean hasFolders() {
        return !getAll().isEmpty();
    }

    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    // --- Membership ---

    public void addToFolder(String folderId, String packageName) {
        Map<String, List<String>> members = getAllMembers();
        // Remove from any existing folder first (one folder per item)
        for (List<String> list : members.values()) {
            list.remove(packageName);
        }
        List<String> folderMembers = members.get(folderId);
        if (folderMembers == null) {
            folderMembers = new ArrayList<>();
            members.put(folderId, folderMembers);
        }
        if (!folderMembers.contains(packageName)) {
            folderMembers.add(packageName);
        }
        persistMembers(members);
    }

    public void removeFromFolder(String folderId, String packageName) {
        Map<String, List<String>> members = getAllMembers();
        List<String> folderMembers = members.get(folderId);
        if (folderMembers != null) {
            folderMembers.remove(packageName);
            persistMembers(members);
        }
    }

    public List<String> getMembers(String folderId) {
        Map<String, List<String>> members = getAllMembers();
        List<String> result = members.get(folderId);
        return result != null ? new ArrayList<>(result) : new ArrayList<>();
    }

    public String getFolderForPackage(String packageName) {
        Map<String, List<String>> members = getAllMembers();
        for (Map.Entry<String, List<String>> entry : members.entrySet()) {
            if (entry.getValue().contains(packageName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean isInAnyFolder(String packageName) {
        return getFolderForPackage(packageName) != null;
    }

    public void removePackageFromAllFolders(String packageName) {
        Map<String, List<String>> members = getAllMembers();
        boolean changed = false;
        for (List<String> list : members.values()) {
            if (list.remove(packageName)) {
                changed = true;
            }
        }
        if (changed) {
            persistMembers(members);
        }
    }

    // --- Icon management ---

    public void saveIcon(String folderId, Bitmap bitmap) {
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, ICON_SIZE, ICON_SIZE, true);
            File file = new File(iconDir, folderId + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            if (scaled != bitmap) {
                scaled.recycle();
            }
        } catch (Exception ignored) {
        }
    }

    public Drawable loadIconDrawable(String folderId) {
        File file = new File(iconDir, folderId + ".png");
        if (!file.exists()) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return null;
        return new BitmapDrawable(resources, bitmap);
    }

    public void deleteIcon(String folderId) {
        File file = new File(iconDir, folderId + ".png");
        if (file.exists()) {
            file.delete();
        }
    }

    // --- Private helpers ---

    private void persistFolders(List<FolderData> all) {
        try {
            JSONArray arr = new JSONArray();
            for (FolderData f : all) {
                JSONObject obj = new JSONObject();
                obj.put("id", f.id);
                obj.put("label", f.label);
                obj.put("hasCustomIcon", f.hasCustomIcon);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_FOLDERS, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private Map<String, List<String>> getAllMembers() {
        Map<String, List<String>> result = new HashMap<>();
        String json = prefs.getString(KEY_MEMBERS, "{}");
        try {
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String folderId = keys.next();
                JSONArray arr = obj.getJSONArray(folderId);
                List<String> packages = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    packages.add(arr.getString(i));
                }
                result.put(folderId, packages);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void persistMembers(Map<String, List<String>> members) {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, List<String>> entry : members.entrySet()) {
                JSONArray arr = new JSONArray();
                for (String pkg : entry.getValue()) {
                    arr.put(pkg);
                }
                obj.put(entry.getKey(), arr);
            }
            prefs.edit().putString(KEY_MEMBERS, obj.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
