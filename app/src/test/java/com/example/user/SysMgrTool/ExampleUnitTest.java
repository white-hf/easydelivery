package com.example.user.SysMgrTool;

import org.junit.Test;

import static org.junit.Assert.*;

import com.uniuni.SysMgrTool.common.FileLog;
import com.uniuni.SysMgrTool.MySingleton;
import com.uniuni.SysMgrTool.Response.OrderDetailData;
import com.uniuni.SysMgrTool.Response.OrdersOfDetail;
import com.uniuni.SysMgrTool.View.OderDetailView;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void self_pick()
    {
        OrderDetailData o = new OrderDetailData();
        OrdersOfDetail od = new OrdersOfDetail();
        o.setOrders(od);
        o.getOrders().setOrder_id(111);
        o.getOrders().setOrder_sn("1234567");

        OderDetailView v = new OderDetailView(o);
        v.handleSelfPick();
    }

    @Test
    public void scan_parcel()
    {

    }

    @Test
    public void self_putinStorage()
    {
        MySingleton s = new MySingleton();
        s.onCreate();

        OrderDetailData o = new OrderDetailData();
        OrdersOfDetail od = new OrdersOfDetail();
        o.setOrders(od);
        o.getOrders().setOrder_id(111);
        o.getOrders().setOrder_sn("1234567");

        OderDetailView v = new OderDetailView(o);
    }

    @Test
    public void testLog()
    {
        FileLog.getInstance();
        FileLog.getInstance().init();
        FileLog.getInstance().writeLog("This is a log");
    }



}