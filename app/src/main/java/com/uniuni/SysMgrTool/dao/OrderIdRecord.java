package com.uniuni.SysMgrTool.dao;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "orderid_record")
public class OrderIdRecord {
    @PrimaryKey
    @NonNull
    public String tid;
    public Date createDate;
}
