package com.hf.easydelivery.core;


import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.dao.DeliveryInfo;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * PowerSaverSelector
 * 极简省电策略下的“下一批候选包裹”选择器：
 * 1) 同址优先（街名 + 门牌完全一致，排除当前单）
 * 2) 其余按距离升序补足
 * 3) 结果数量 ≤ 同址数量 + extraNearCount
 *
 * 仅做纯选择逻辑，不依赖 UI/地图/相机。
 */
public final class PowerSaverSelector {

    private static final String TAG = "PowerSaverSelector";

    public static final class Params {
        /** 非同址的补充数量（同址数量 + 本数值 为上限） */
        public int extraNearCount = 3;
        /** 对“非同址”补充的可选半径；≤0 表示不限制 */
        public float nearRadiusMeters = 150f;
    }

    public PowerSaverSelector() {}

    /**
     * 从 deliveryinfoMgr 的未派送列表中，基于“刚送达的 current”选出下一批候选。
     */
    public @NonNull List<DeliveryInfo> selectNext(
            @NonNull DeliveryInfo current,
            @Nullable Location currentLoc,
            @NonNull DeliveryinfoMgr deliveryinfoMgr,
            @NonNull Params params
    ) {
        List<DeliveryInfo> all = safeList(deliveryinfoMgr.getListDeliveryInfo());
        if (all.isEmpty()) return Collections.emptyList();

        // 读取 current 的关键字段
        final Long currentId = current.getOrderId();
        final String street = safeLower(normalizeStreet(current.getStreetName(), current.getAddress()));
        final Integer civil = current.getCivilNumber();

        // 参照定位（优先当前定位，回退到 current 的坐标）
        double refLat = Double.NaN, refLng = Double.NaN;
        if (currentLoc != null) {
            refLat = currentLoc.getLatitude();
            refLng = currentLoc.getLongitude();
        } else {
            try {
                refLat = current.getLatitude();
                refLng = current.getLongitude();
            } catch (Throwable ignore) {}
        }

        List<DeliveryInfo> sameAddress = new ArrayList<>();
        List<DeliveryInfo> others = new ArrayList<>();

        for (DeliveryInfo info : all) {
            if (info == null) continue;
            if (currentId != null && currentId.equals(info.getOrderId())) continue; // 排除当前
            String s2 = safeLower(normalizeStreet(info.getStreetName(), info.getAddress()));
            Integer c2 = info.getCivilNumber();
            boolean same = (street.equals(s2)) && ((civil != null && civil > 0) ? civil.equals(c2) : (c2 == null || c2 <= 0));
            if (same) {
                sameAddress.add(info);
            } else {
                others.add(info);
            }
        }

        // 同址内部稳定排序（单元号/路由号/原序）
        Collections.sort(sameAddress, new Comparator<DeliveryInfo>() {
            @Override
            public int compare(DeliveryInfo a, DeliveryInfo b) {
                int unit = safeUnitKey(a).compareTo(safeUnitKey(b));
                if (unit != 0) return unit;
                String ra = safeLower(str(a.getRouteNumber()));
                String rb = safeLower(str(b.getRouteNumber()));
                int r = ra.compareTo(rb);
                if (r != 0) return r;
                return Long.compare(safeId(a), safeId(b));
            }
        });

        // 其余按距离升序（若无参照坐标则只返回同址）
        List<Scored> scoredOthers = new ArrayList<>();
        if (!Double.isNaN(refLat) && !Double.isNaN(refLng)) {
            for (DeliveryInfo info : others) {
                double ilat = info.getLatitude();
                double ilng = info.getLongitude();
                if (Math.abs(ilat) < 1e-6 && Math.abs(ilng) < 1e-6) continue; // 无效坐标跳过
                float dist = distanceMeters(refLat, refLng, ilat, ilng);
                if (params.nearRadiusMeters > 0 && dist > params.nearRadiusMeters) continue; // 半径过滤（可选）
                scoredOthers.add(new Scored(info, dist));
            }
            Collections.sort(scoredOthers, Comparator.comparingDouble(s -> s.dist));
        }

        // 组装结果：同址 + 其余前 extraNearCount
        List<DeliveryInfo> result = new ArrayList<>(sameAddress);
        int quota = Math.max(0, params.extraNearCount);
        for (int i = 0; i < scoredOthers.size() && i < quota; i++) {
            result.add(scoredOthers.get(i).info);
        }

        FileLog.getInstance().debug(TAG, String.format(Locale.getDefault(),
                "selectNext: current=%d same=%d others=%d result=%d ref(%.5f,%.5f)",
                currentId == null ? -1L : currentId,
                sameAddress.size(), scoredOthers.size(), result.size(), refLat, refLng));

        return result;
    }

    // ---------- 工具 ----------

    private static List<DeliveryInfo> safeList(List<DeliveryInfo> src) {
        return src == null ? Collections.emptyList() : new ArrayList<>(src);
    }

    private static String normalizeStreet(String streetName, String fallbackAddr) {
        String s = streetName;
        if (s == null || s.trim().isEmpty()) s = fallbackAddr;
        if (s == null) return "";
        return s.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long safeId(DeliveryInfo d) {
        return d.getOrderId() == null ? Long.MAX_VALUE : d.getOrderId();
    }

    private static String safeUnitKey(DeliveryInfo d) {
        String u = d.getUnitNumber();
        if (u == null) return "\uFFFF"; // 空排最后
        String digits = u.replaceAll("[^0-9]", "");
        String normalized = u.trim().toLowerCase(Locale.US);
        return (digits.isEmpty() ? "ZZZ" : String.format(Locale.US, "%06d", tryParseInt(digits, 999999)))
                + "|" + normalized;
    }

    private static int tryParseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }

    private static float distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        float[] res = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, res);
        return res[0];
    }

    private static final class Scored {
        final DeliveryInfo info;
        final float dist;
        Scored(DeliveryInfo i, float d) { info = i; dist = d; }
    }
}