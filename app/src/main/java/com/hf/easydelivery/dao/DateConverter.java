package com.hf.easydelivery.dao;

import androidx.room.TypeConverter;

import java.util.Date;

public class DateConverter {
    @TypeConverter
    public static Date revertDate(long value) {
        return new Date(value);
    }

    @TypeConverter
    public static long converterDate(Date value) {
        if ( value != null)
            return value.getTime();
        else
            return 0L;
    }
}
