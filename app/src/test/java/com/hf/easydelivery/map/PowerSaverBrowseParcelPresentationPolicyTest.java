package com.hf.easydelivery.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hf.easydelivery.dao.DeliveryInfo;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PowerSaverBrowseParcelPresentationPolicyTest {

    private final PowerSaverBrowseParcelPresentationPolicy policy =
            new PowerSaverBrowseParcelPresentationPolicy();

    @Test
    public void primaryDeliveryIsPrioritized() {
        DeliveryInfo primary = parcel("P1", 44.6450, -63.5750);
        DeliveryInfo other = parcel("P2", 44.7000, -63.5000);
        List<DeliveryInfo> result = policy.selectVisibleParcels(
                Arrays.asList(other, primary),
                location(44.6460, -63.5752),
                primary);
        assertEquals("P1", result.get(0).getRouteNumber());
    }

    @Test
    public void doesNotHideFartherMarkers() {
        List<DeliveryInfo> items = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            items.add(parcel("R" + i, 44.64 + i * 0.001, -63.57));
        }
        List<DeliveryInfo> result = policy.selectVisibleParcels(items, location(44.646, -63.575), null);
        assertEquals(30, result.size());
    }

    @Test
    public void nearerParcelsComeFirstWhenNoPrimary() {
        DeliveryInfo near = parcel("NEAR", 44.6461, -63.5750);
        DeliveryInfo far = parcel("FAR", 44.7461, -63.5750);
        List<DeliveryInfo> result = policy.selectVisibleParcels(
                Arrays.asList(far, near),
                location(44.6460, -63.5750),
                null);
        assertEquals("NEAR", result.get(0).getRouteNumber());
        assertTrue(result.indexOf(near) < result.indexOf(far));
    }

    @Test
    public void parcelsNearCurrentPrimaryArePrioritizedAheadOfFartherOnes() {
        DeliveryInfo primary = parcel("P1", 44.6450, -63.5750);
        primary.setAddress("98 KING ST");
        DeliveryInfo sameAddress = parcel("P2", 44.64502, -63.57502);
        sameAddress.setAddress("98 KING ST");
        DeliveryInfo nearerButDifferent = parcel("P3", 44.64505, -63.57505);
        nearerButDifferent.setAddress("12 OTHER ST");

        List<DeliveryInfo> result = policy.selectVisibleParcels(
                Arrays.asList(nearerButDifferent, sameAddress, primary),
                location(44.6460, -63.5752),
                primary);
        assertEquals("P1", result.get(0).getRouteNumber());
        assertEquals("P2", result.get(1).getRouteNumber());
    }

    private DeliveryInfo parcel(String route, double lat, double lng) {
        DeliveryInfo info = new DeliveryInfo();
        info.setRouteNumber(route);
        info.setLatitude(lat);
        info.setLongitude(lng);
        info.setOrderSn(route);
        return info;
    }

    private DriverLocationSnapshot location(double lat, double lng) {
        return DriverLocationSnapshot.of(lat, lng);
    }
}
