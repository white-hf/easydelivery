package com.hf.easydelivery.dao;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "apartment_photos",
        indices = {@Index(value = {"addressKey"}, unique = true)})
public class ApartmentPhotoEntity {
    public static final String SOURCE_AUTO = "AUTO";
    public static final String SOURCE_MANUAL = "MANUAL";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String addressKey = "";

    public String displayAddress;

    public String filePath;

    public long savedAt;

    public long lastUsedAt;

    @NonNull
    public String source = SOURCE_AUTO;
}
