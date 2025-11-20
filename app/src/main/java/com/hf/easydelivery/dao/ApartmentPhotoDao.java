package com.hf.easydelivery.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ApartmentPhotoDao {

    @Query("SELECT * FROM apartment_photos WHERE addressKey = :addressKey LIMIT 1")
    ApartmentPhotoEntity findByKey(String addressKey);

    @Query("SELECT * FROM apartment_photos WHERE id = :id LIMIT 1")
    ApartmentPhotoEntity findById(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ApartmentPhotoEntity entity);

    @Update
    void update(ApartmentPhotoEntity entity);

    @Query("DELETE FROM apartment_photos WHERE id = :id")
    void delete(long id);

    @Query("SELECT * FROM apartment_photos ORDER BY savedAt DESC")
    List<ApartmentPhotoEntity> listAll();

    @Query("UPDATE apartment_photos SET lastUsedAt = :timestamp WHERE id = :id")
    void updateLastUsed(long id, long timestamp);
}
