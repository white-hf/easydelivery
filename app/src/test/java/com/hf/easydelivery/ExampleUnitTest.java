package com.hf.easydelivery;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.hf.courierservice.apihelper.FileLog;
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
        Utils.AddressInfo apartmentHyphen = extractApartmentAndStreetNumber("1001-67 Kings Wharf Pl, Dartmouth, NS B2Y 4R9");
        Utils.AddressInfo plainHouse = extractApartmentAndStreetNumber("98 King St, Dartmouth, NS, CA, B2Y 2S1");
        Utils.AddressInfo trailingUnit = extractApartmentAndStreetNumber("117 Richmond St 409 DARTMOUTH NS");
        Utils.AddressInfo spacedPostalHouse = extractApartmentAndStreetNumber("10 TRINAH COURT, HALIFAX, NS, CA, B2W 6J7");
        Utils.AddressInfo malformedHyphenHouse = extractApartmentAndStreetNumber("Street - 49 Loggen Rd Middle Sackville NS");

        assertEquals("1001", apartmentHyphen.getApartmentNumber());
        assertEquals("67", apartmentHyphen.getStreetNumber());
        assertTrue(apartmentHyphen.hasConfidentUnit());

        assertEquals("", plainHouse.getApartmentNumber());
        assertEquals("98", plainHouse.getStreetNumber());
        assertFalse(plainHouse.hasConfidentUnit());

        assertEquals("409", trailingUnit.getApartmentNumber());
        assertEquals("117", trailingUnit.getStreetNumber());
        assertTrue(trailingUnit.hasConfidentUnit());

        assertEquals("", spacedPostalHouse.getApartmentNumber());
        assertEquals("10", spacedPostalHouse.getStreetNumber());
        assertFalse(spacedPostalHouse.hasConfidentUnit());

        assertEquals("", malformedHyphenHouse.getApartmentNumber());
        assertEquals("49", malformedHyphenHouse.getStreetNumber());
        assertFalse(malformedHyphenHouse.hasConfidentUnit());
    }
    private Utils.AddressInfo extractApartmentAndStreetNumber(String address) {
        return Utils.extractApartmentAndStreetNumber(address);

    }

    private Context getApplicationContext() {
        return null;
    }
}
