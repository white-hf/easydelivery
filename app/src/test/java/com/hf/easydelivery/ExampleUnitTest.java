package com.hf.easydelivery;

import org.junit.Test;

import static org.junit.Assert.*;

import android.content.Context;

import com.hf.easydelivery.common.FileLog;
import com.hf.easydelivery.common.Utils;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    @Test
    public void testLog()
    {
        FileLog.getInstance();
        FileLog.getInstance().init(getApplicationContext());
        FileLog.getInstance().writeLog("This is a log");
    }

    @Test
    public void testUtils()
    {
        String address1 = "1001-67 Kings Wharf Pl, Dartmouth, NS B2Y 4R9";
        String address2 = "67 Kings Wharf Pl, Dartmouth, NS B2Y 4R9";
        String address3 = "42 blr lake road, darmt, NS B3A 3Y1";
        String address4 = "117 Richmond St 409 DARTMOUTH NS";
        String address5 = "301 - 101 Ochterloney Street Dartmouth NS";
        String address6 = "9B3B 2J7"; // Example where apartment number is invalid due to letter following it

        Utils.AddressInfo addressInfo1 = extractApartmentAndStreetNumber(address1);
        Utils.AddressInfo addressInfo2 = extractApartmentAndStreetNumber(address2);
        Utils.AddressInfo addressInfo3 = extractApartmentAndStreetNumber(address3);
        Utils.AddressInfo addressInfo4 = extractApartmentAndStreetNumber(address4);
        Utils.AddressInfo addressInfo5 = extractApartmentAndStreetNumber(address5);
        Utils.AddressInfo addressInfo6 = extractApartmentAndStreetNumber(address6);

        System.out.println("Address 1 - 公寓单元号: " + addressInfo1.getApartmentNumber() + ", 街道号: " + addressInfo1.getStreetNumber());
        System.out.println("Address 2 - 公寓单元号: " + addressInfo2.getApartmentNumber() + ", 街道号: " + addressInfo2.getStreetNumber());
        System.out.println("Address 3 - 公寓单元号: " + addressInfo3.getApartmentNumber() + ", 街道号: " + addressInfo3.getStreetNumber());
        System.out.println("Address 4 - 公寓单元号: " + addressInfo4.getApartmentNumber() + ", 街道号: " + addressInfo4.getStreetNumber());
        System.out.println("Address 5 - 公寓单元号: " + addressInfo5.getApartmentNumber() + ", 街道号: " + addressInfo5.getStreetNumber());
        System.out.println("Address 6 - 公寓单元号: " + addressInfo6.getApartmentNumber() + ", 街道号: " + addressInfo6.getStreetNumber());

    }
    private Utils.AddressInfo extractApartmentAndStreetNumber(String address) {
        return Utils.extractApartmentAndStreetNumber(address);

    }

    private Context getApplicationContext() {
        return null;
    }
}
