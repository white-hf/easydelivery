package com.example.user.SysMgrTool;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import com.uniuni.SysMgrTool.MyDb;
import com.uniuni.SysMgrTool.dao.ScannedRecord;
import com.uniuni.SysMgrTool.dao.ScannedRecordDao;


/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals("com.example.user.myapplication", appContext.getPackageName());
    }

    @Test
    public void dbTest()
    {
        MyDb db = new MyDb();
        Context appContext = InstrumentationRegistry.getTargetContext();
        db.initDb(appContext);

        ScannedRecordDao dao = db.getScannedRecordDao();
        ScannedRecord[] records = new ScannedRecord[2];
        ScannedRecord r0 = records[0];
        r0.setOrderId(100);
        r0.setBatchId("b100");
        r0.setPickId("1");
        r0.setStaffId(600);

        r0.setCommitted(0);

        ScannedRecord r1 = records[1];
        r1.setOrderId(200);
        r1.setBatchId("b100");
        r1.setPickId(2);
        r1.setStaffId(600);

        r1.setCommitted(Short.valueOf(0));

        dao.addRecord(r0);


    }
}
