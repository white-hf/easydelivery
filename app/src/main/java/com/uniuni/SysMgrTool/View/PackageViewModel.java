package com.uniuni.SysMgrTool.View;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.dao.DeliveredPackagesDao;
import com.uniuni.SysMgrTool.dao.PackageEntity;

import java.util.List;

public class PackageViewModel extends AndroidViewModel {
    private DeliveredPackagesDao repository;
    private LiveData<List<PackageEntity>> packagesByStatus;

    public PackageViewModel(@NonNull Application application) {
        super(application);
        repository = MySingleton.getInstance().getmMydb().getDeliveredPackagesDao();
    }

    public LiveData<List<PackageEntity>> getPackagesByStatus(String status) {
        packagesByStatus = repository.getPackagesByStatus(status);
        return packagesByStatus;
    }
}
