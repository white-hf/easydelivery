package com.hf.easydelivery.apartment;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.hf.easydelivery.ResourceMgr;
import com.hf.easydelivery.dao.ApartmentPhotoDao;
import com.hf.easydelivery.dao.ApartmentPhotoEntity;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

public class ApartmentPhotoRepository {

    private final ApartmentPhotoDao dao;
    private final Handler dbHandler;

    public ApartmentPhotoRepository() {
        ResourceMgr mgr = ResourceMgr.getInstance();
        dao = mgr.getmMydb() != null ? mgr.getmMydb().getApartmentPhotoDao() : null;
        dbHandler = mgr.getDbHandler();
    }

    private <T> T execute(Callable<T> callable) {
        if (dao == null || dbHandler == null) return null;
        if (Looper.myLooper() == dbHandler.getLooper()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        CountDownLatch latch = new CountDownLatch(1);
        final Object[] holder = new Object[1];
        final RuntimeException[] error = new RuntimeException[1];
        dbHandler.post(() -> {
            try {
                holder[0] = callable.call();
            } catch (Exception e) {
                error[0] = e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        if (error[0] != null) throw error[0];
        @SuppressWarnings("unchecked")
        T result = (T) holder[0];
        return result;
    }

    private void run(Runnable runnable) {
        execute(() -> {
            runnable.run();
            return null;
        });
    }

    @Nullable
    public ApartmentPhotoEntity findByKey(String key) {
        return execute(() -> dao.findByKey(key));
    }

    public long upsert(ApartmentPhotoEntity entity) {
        Long id = execute(() -> dao.insert(entity));
        return id == null ? -1L : id;
    }

    public void delete(long id) {
        run(() -> dao.delete(id));
    }

    public void updateLastUsed(long id, long timestamp) {
        run(() -> dao.updateLastUsed(id, timestamp));
    }

    public List<ApartmentPhotoEntity> listAll() {
        return execute(dao::listAll);
    }

    @Nullable
    public ApartmentPhotoEntity findById(long id) {
        return execute(() -> dao.findById(id));
    }

    public void update(ApartmentPhotoEntity entity) {
        run(() -> dao.update(entity));
    }
}
