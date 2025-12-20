package com.hf.easydelivery.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.hf.easydelivery.dao.DeliveryInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight store for “My Large Parcels”.
 * - Single active dataset (no history), overwritten on clear or new session.
 * - Stores only tracking/waybill + route number for badge checks.
 */
public class LargeParcelStore {
    private static final String PREF_NAME = "large_parcels_pref";
    private static final String KEY_ENTRIES_JSON = "entries_json";
    private static final String KEY_DAY = "day";
    private static final String KEY_LAST_SAVED_AT = "last_saved_at"; // epoch millis

    public static class Entry {
        public final String tracking;
        public final String route;

        public Entry(@NonNull String tracking, @NonNull String route) {
            this.tracking = tracking;
            this.route = route;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static void ensureFreshDay(Context ctx) {
        long now = System.currentTimeMillis();
        long today = now / (24 * 60 * 60 * 1000L);
        SharedPreferences p = prefs(ctx);
        long savedDay = p.getLong(KEY_DAY, -1);
        long lastSavedAt = p.getLong(KEY_LAST_SAVED_AT, -1);

        // 若最近写入在 18h 内，认为同一工作日，防止时区/校时抖动导致误清空
        if (lastSavedAt > 0 && (now - lastSavedAt) < 18 * 60 * 60 * 1000L) {
            return;
        }

        if (savedDay != today) {
            p.edit()
                    .remove(KEY_ENTRIES_JSON)
                    .putLong(KEY_DAY, today)
                    .remove(KEY_LAST_SAVED_AT)
                    .apply();
        }
    }

    public static void clear(Context ctx) {
        prefs(ctx).edit()
                .remove(KEY_ENTRIES_JSON)
                .remove(KEY_DAY)
                .remove(KEY_LAST_SAVED_AT)
                .apply();
    }

    public static void add(Context ctx, @NonNull String tracking, @NonNull String route) {
        ensureFreshDay(ctx);
        String t = tracking.trim();
        String r = route.trim();
        if (TextUtils.isEmpty(t)) return;

        List<Entry> list = loadInternal(ctx);
        // 去重后插入顶部
        List<Entry> newList = new ArrayList<>();
        newList.add(new Entry(t, r));
        for (Entry e : list) {
            if (t.equalsIgnoreCase(e.tracking)) continue;
            newList.add(e);
        }
        saveInternal(ctx, newList);
    }

    public static List<Entry> list(Context ctx) {
        ensureFreshDay(ctx);
        return loadInternal(ctx);
    }

    public static boolean isLarge(Context ctx, DeliveryInfo info) {
        if (info == null) return false;
        List<Entry> list = list(ctx);
        if (list.isEmpty()) return false;
        String tracking = info.getOrderSn() == null ? "" : info.getOrderSn().trim();
        String route = info.getRouteNumber() == null ? "" : info.getRouteNumber().trim();
        if (TextUtils.isEmpty(tracking)) return false;
        for (Entry e : list) {
            if (tracking.equalsIgnoreCase(e.tracking)) {
                return true;
            }
        }
        return false;
    }

    // --- internal helpers (ordered JSON storage) ---
    private static List<Entry> loadInternal(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String json = p.getString(KEY_ENTRIES_JSON, null);
        List<Entry> list = new ArrayList<>();
        if (!TextUtils.isEmpty(json)) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    String t = obj.optString("tracking", "");
                    String r = obj.optString("route", "");
                    if (!TextUtils.isEmpty(t)) {
                        list.add(new Entry(t, r));
                    }
                }
            } catch (Exception ignore) { }
        }
        return list;
    }

    private static void saveInternal(Context ctx, List<Entry> list) {
        SharedPreferences p = prefs(ctx);
        org.json.JSONArray arr = new org.json.JSONArray();
        for (Entry e : list) {
            org.json.JSONObject obj = new org.json.JSONObject();
            try {
                obj.put("tracking", e.tracking);
                obj.put("route", e.route);
            } catch (Exception ignore) { }
            arr.put(obj);
        }
        long now = System.currentTimeMillis();
        long today = now / (24 * 60 * 60 * 1000L);
        p.edit()
                .putString(KEY_ENTRIES_JSON, arr.toString())
                .putLong(KEY_DAY, today)
                .putLong(KEY_LAST_SAVED_AT, now)
                .apply();
    }
}
