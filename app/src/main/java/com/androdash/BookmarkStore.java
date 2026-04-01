package com.androdash;

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
import java.util.List;
import java.util.UUID;

public class BookmarkStore {

    private static final String PREFS_NAME = "androdash_prefs";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String ICON_DIR = "bookmark_icons";
    private static final int ICON_SIZE = 96;

    private final SharedPreferences prefs;
    private final File iconDir;
    private final Resources resources;

    public static class BookmarkData {
        public String id;
        public String label;
        public String url;
        public boolean hasCustomIcon;

        public BookmarkData(String id, String label, String url, boolean hasCustomIcon) {
            this.id = id;
            this.label = label;
            this.url = url;
            this.hasCustomIcon = hasCustomIcon;
        }

        public String getPackageName() {
            return "bookmark://" + id;
        }
    }

    public BookmarkStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        iconDir = new File(context.getFilesDir(), ICON_DIR);
        resources = context.getResources();
        if (!iconDir.exists()) {
            iconDir.mkdirs();
        }
    }

    public List<BookmarkData> getAll() {
        List<BookmarkData> result = new ArrayList<>();
        String json = prefs.getString(KEY_BOOKMARKS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                result.add(new BookmarkData(
                        obj.getString("id"),
                        obj.getString("label"),
                        obj.getString("url"),
                        obj.optBoolean("hasCustomIcon", false)));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public void save(BookmarkData bookmark) {
        List<BookmarkData> all = getAll();
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(bookmark.id)) {
                all.set(i, bookmark);
                found = true;
                break;
            }
        }
        if (!found) {
            all.add(bookmark);
        }
        persist(all);
    }

    public void delete(String id) {
        List<BookmarkData> all = getAll();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).id.equals(id)) {
                all.remove(i);
            }
        }
        persist(all);
        deleteIcon(id);
    }

    public BookmarkData getById(String id) {
        for (BookmarkData b : getAll()) {
            if (b.id.equals(id)) return b;
        }
        return null;
    }

    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    // --- Icon management ---

    public void saveIcon(String bookmarkId, Bitmap bitmap) {
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, ICON_SIZE, ICON_SIZE, true);
            File file = new File(iconDir, bookmarkId + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            if (scaled != bitmap) {
                scaled.recycle();
            }
        } catch (Exception ignored) {
        }
    }

    public Drawable loadIconDrawable(String bookmarkId) {
        File file = new File(iconDir, bookmarkId + ".png");
        if (!file.exists()) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return null;
        return new BitmapDrawable(resources, bitmap);
    }

    public void deleteIcon(String bookmarkId) {
        File file = new File(iconDir, bookmarkId + ".png");
        if (file.exists()) {
            file.delete();
        }
    }

    // --- Private helpers ---

    private void persist(List<BookmarkData> all) {
        try {
            JSONArray arr = new JSONArray();
            for (BookmarkData b : all) {
                JSONObject obj = new JSONObject();
                obj.put("id", b.id);
                obj.put("label", b.label);
                obj.put("url", b.url);
                obj.put("hasCustomIcon", b.hasCustomIcon);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_BOOKMARKS, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
