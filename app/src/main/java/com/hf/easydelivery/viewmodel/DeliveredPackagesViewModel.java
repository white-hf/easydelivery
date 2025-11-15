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
    private final DeliveredPackagesDao deliveredPackagesDao;
    private final MutableLiveData<Long> selectedDate = new MutableLiveData<>();
    private final LiveData<List<PackageEntity>> deliveredPackages;

    public DeliveredPackagesViewModel() {
        deliveredPackagesDao = ResourceMgr.getInstance().getmMydb().getDeliveredPackagesDao();

        deliveredPackages = Transformations.switchMap(selectedDate, date -> {
            try {
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

                List<String> statuses = Arrays.asList(
                    PendingPackagesMgr.PackageStatus.UPLOADED.getStatus(),
                    PendingPackagesMgr.PackageStatus.FAILED.getStatus()
                );

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
    }

    public LiveData<Long> getSelectedDate() {
        return selectedDate;
    }
}
