package com.hf.easydelivery.map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hf.easydelivery.dao.DeliveryInfo;

import org.junit.Test;

public class PowerSaverBrowseMarkerStylePolicyTest {

    private final MarkerStylePolicy policy = new PowerSaverBrowseMarkerStylePolicy();

    @Test
    public void browseModeUsesCompactMarkers() {
        DeliveryInfo info = parcel("21");
        MarkerStyleDecision decision = policy.styleFor(info, null, false, MarkerColorTone.DELIVERING);
        assertTrue(decision.compact);
        assertFalse(decision.highlighted);
        assertFalse(decision.largeParcel);
    }

    @Test
    public void primaryAndLargeParcelStateArePreserved() {
        DeliveryInfo primary = parcel("7");
        MarkerStyleDecision decision = policy.styleFor(primary, primary, true, MarkerColorTone.UNSCANNED);
        assertTrue(decision.compact);
        assertTrue(decision.highlighted);
        assertTrue(decision.largeParcel);
    }

    private DeliveryInfo parcel(String route) {
        DeliveryInfo info = new DeliveryInfo();
        info.setRouteNumber(route);
        info.setOrderSn(route);
        return info;
    }
}
