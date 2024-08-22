package com.hf.easydelivery.view;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.dao.DeliveredPackagesDao;
import com.hf.easydelivery.dao.PackageEntity;

import java.util.List;

public class PackageViewModel extends AndroidViewModel {
    private DeliveredPackagesDao repository;
    private LiveData<List<PackageEntity>> packagesByStatus;

    public PackageViewModel(@NonNull Application application) {
        super(application);
        repository = ResourceMgr.getInstance().getmMydb().getDeliveredPackagesDao();
    }

    public LiveData<List<PackageEntity>> getPackagesByStatus(String status) {
        packagesByStatus = repository.getPackagesByStatus(status);
        return packagesByStatus;
    }
}
