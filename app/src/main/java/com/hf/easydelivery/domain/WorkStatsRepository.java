package com.hf.easydelivery.domain;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.dao.DailyWorkStatsDao;
import com.hf.easydelivery.dao.DailyWorkStatsEntity;
import com.hf.easydelivery.dao.DeliveredPackagesDao;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkStatsRepository {

    private final DailyWorkStatsDao dailyWorkStatsDao;
    private final DeliveredPackagesDao deliveredPackagesDao;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final TimeZone timeZone;

    public WorkStatsRepository(@NonNull Context context) {
        this.dailyWorkStatsDao = ResourceMgr.getInstance().getmMydb().getDailyWorkStatsDao();
        this.deliveredPackagesDao = ResourceMgr.getInstance().getmMydb().getDeliveredPackagesDao();
        this.timeZone = TimeZone.getDefault();
    }

    public void addDistanceMeters(double meters, long timestampMillis) {
        if (meters <= 0) return;
        final Integer driverId = getDriverId();
        if (driverId == null || driverId <= 0) return;
        final long dateKey = toDateKey(timestampMillis);
        final long now = System.currentTimeMillis();
        ioExecutor.execute(() -> {
            DailyWorkStatsEntity entity = dailyWorkStatsDao.getForDate(driverId, dateKey);
            if (entity == null) {
                entity = new DailyWorkStatsEntity(driverId, dateKey, meters, now);
            } else {
                entity.totalDistanceMeters += meters;
                entity.lastUpdated = now;
            }
            dailyWorkStatsDao.upsert(entity);
        });
    }

    public interface SummaryCallback {
        void onResult(WorkSummary summary);
    }

    public interface MonthSummaryCallback {
        void onResult(MonthSummary summary);
    }

    public void getDailySummary(long dateKey, @NonNull SummaryCallback callback) {
        final Integer driverId = getDriverId();
        if (driverId == null || driverId <= 0) {
            callback.onResult(new WorkSummary(dateKey, 0, 0d));
            return;
        }
        ioExecutor.execute(() -> {
            Double meters = dailyWorkStatsDao.getDistanceMeters(driverId, dateKey);
            if (meters == null) meters = 0d;
            long[] range = getDayRange(dateKey);
            int parcels = deliveredPackagesDao.countDeliveredByDriverAndTime(driverId.shortValue(), range[0], range[1]);
            WorkSummary summary = new WorkSummary(dateKey, parcels, meters);
            callback.onResult(summary);
        });
    }

    public void getMonthlySummary(int year, int month, @NonNull MonthSummaryCallback callback) {
        final Integer driverId = getDriverId();
        if (driverId == null || driverId <= 0) {
            callback.onResult(MonthSummary.empty(year, month));
            return;
        }
        ioExecutor.execute(() -> {
            long startKey = dateKey(year, month, 1);
            int daysInMonth = daysInMonth(year, month);
            long endKey = dateKey(year, month, daysInMonth);
            List<DailyWorkStatsEntity> entities = dailyWorkStatsDao.getForRange(driverId, startKey, endKey);
            Map<Long, Double> distanceMap = new HashMap<>();
            if (entities != null) {
                for (DailyWorkStatsEntity entity : entities) {
                    if (entity != null && entity.statDate != null) {
                        distanceMap.put(entity.statDate, entity.totalDistanceMeters);
                    }
                }
            }

            List<WorkDayStat> dayStats = new ArrayList<>();
            double totalDistance = 0d;
            int totalParcels = 0;
            int workingDays = 0;

            for (int day = 1; day <= daysInMonth; day++) {
                long dayKey = dateKey(year, month, day);
                double meters = distanceMap.getOrDefault(dayKey, 0d);
                long[] range = getDayRange(dayKey);
                int parcels = deliveredPackagesDao.countDeliveredByDriverAndTime(driverId.shortValue(), range[0], range[1]);
                if (meters > 0.1d || parcels > 0) {
                    workingDays++;
                    dayStats.add(new WorkDayStat(dayKey, parcels, meters));
                }
                totalDistance += meters;
                totalParcels += parcels;
            }

            MonthSummary summary = new MonthSummary(year, month, dayStats, workingDays, totalParcels, totalDistance);
            callback.onResult(summary);
        });
    }

    private Integer getDriverId() {
        ResourceMgr.LoginInfo info = ResourceMgr.getInstance().getLoginInfo();
        return info != null ? info.loginId : null;
    }

    private long toDateKey(long timeMillis) {
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(timeMillis);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        return year * 10000L + month * 100 + day;
    }

    private long[] getDayRange(long dateKey) {
        int year = (int) (dateKey / 10000);
        int month = (int) ((dateKey % 10000) / 100) - 1;
        int day = (int) (dateKey % 100);
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.clear();
        calendar.set(year, month, day, 0, 0, 0);
        long start = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        long end = calendar.getTimeInMillis();
        return new long[]{start, end};
    }

    private long dateKey(int year, int month, int day) {
        return year * 10000L + month * 100L + day;
    }

    private int daysInMonth(int year, int month) {
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.clear();
        calendar.set(year, month - 1, 1);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    public static class WorkSummary {
        public final long dateKey;
        public final int deliveredPackages;
        public final double distanceMeters;

        public WorkSummary(long dateKey, int deliveredPackages, double distanceMeters) {
            this.dateKey = dateKey;
            this.deliveredPackages = deliveredPackages;
            this.distanceMeters = distanceMeters;
        }

        public double distanceKilometers() {
            return distanceMeters / 1000d;
        }
    }

    public static class WorkDayStat {
        public final long dateKey;
        public final int deliveredPackages;
        public final double distanceMeters;

        public WorkDayStat(long dateKey, int deliveredPackages, double distanceMeters) {
            this.dateKey = dateKey;
            this.deliveredPackages = deliveredPackages;
            this.distanceMeters = distanceMeters;
        }

        public double distanceKilometers() {
            return distanceMeters / 1000d;
        }
    }

    public static class MonthSummary {
        public final int year;
        public final int month;
        public final List<WorkDayStat> dayStats;
        public final int workingDays;
        public final int totalParcels;
        public final double totalDistanceMeters;

        public MonthSummary(int year,
                            int month,
                            List<WorkDayStat> dayStats,
                            int workingDays,
                            int totalParcels,
                            double totalDistanceMeters) {
            this.year = year;
            this.month = month;
            this.dayStats = dayStats;
            this.workingDays = workingDays;
            this.totalParcels = totalParcels;
            this.totalDistanceMeters = totalDistanceMeters;
        }

        public double totalDistanceKilometers() {
            return totalDistanceMeters / 1000d;
        }

        public static MonthSummary empty(int year, int month) {
            return new MonthSummary(year, month, new ArrayList<>(), 0, 0, 0d);
        }
    }
}
