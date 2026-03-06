package com.hf.easydelivery.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.hf.courierservice.apihelper.FileLog;
import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.core.PendingPackagesMgr;
import com.hf.easydelivery.dao.DeliveredPackagesDao;
import com.hf.easydelivery.dao.PackageEntity;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class DeliveredPackagesViewModel extends ViewModel {

    private static final String TAG = "DeliveredPackagesVM";
    public static final String FILTER_ALL = "all";
    private final DeliveredPackagesDao deliveredPackagesDao;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>();
    private final MutableLiveData<String> selectedStatus = new MutableLiveData<>(FILTER_ALL);
    private final MutableLiveData<QueryFilter> queryFilter = new MutableLiveData<>();
    private final LiveData<List<PackageEntity>> deliveredPackages;

    public DeliveredPackagesViewModel() {
        deliveredPackagesDao = ResourceMgr.getInstance().getmMydb().getDeliveredPackagesDao();

        deliveredPackages = Transformations.switchMap(queryFilter, filter -> {
            try {
                Long date = filter == null ? null : filter.date;
                if (date == null) {
                    return new MutableLiveData<>(Collections.emptyList());
                }
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(date);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                long startTime = calendar.getTimeInMillis();

                calendar.add(Calendar.DAY_OF_YEAR, 1);
                long endTime = calendar.getTimeInMillis();

                List<String> statuses = getStatusesForFilter(filter == null ? FILTER_ALL : filter.status);

                return deliveredPackagesDao.getPackagesByStatusAndDate(
                        statuses,
                        startTime,
                        endTime
                );
            } catch (Exception e) {
                FileLog.getInstance().error(TAG, "Error getting delivered packages by date", e);
                return new MutableLiveData<>(Collections.emptyList()); // Return empty list on error
            }
        });
    }

    public LiveData<List<PackageEntity>> getDeliveredPackages() {
        return deliveredPackages;
    }

    public void setSelectedDate(long timeInMillis) {
        selectedDate.setValue(timeInMillis);
        updateQueryFilter();
    }

    public LiveData<Long> getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedStatus(String status) {
        selectedStatus.setValue(status == null ? FILTER_ALL : status);
        updateQueryFilter();
    }

    public LiveData<String> getSelectedStatus() {
        return selectedStatus;
    }

    private void updateQueryFilter() {
        queryFilter.setValue(new QueryFilter(selectedDate.getValue(), selectedStatus.getValue()));
    }

    private List<String> getStatusesForFilter(String filter) {
        if (PendingPackagesMgr.PackageStatus.UPLOADED.getStatus().equals(filter)
                || PendingPackagesMgr.PackageStatus.FAILED.getStatus().equals(filter)
                || PendingPackagesMgr.PackageStatus.Pending.getStatus().equals(filter)) {
            return Collections.singletonList(filter);
        }
        return Arrays.asList(
                PendingPackagesMgr.PackageStatus.Pending.getStatus(),
                PendingPackagesMgr.PackageStatus.UPLOADED.getStatus(),
                PendingPackagesMgr.PackageStatus.FAILED.getStatus()
        );
    }

    private static class QueryFilter {
        final Long date;
        final String status;

        QueryFilter(Long date, String status) {
            this.date = date;
            this.status = status;
        }
    }
}
