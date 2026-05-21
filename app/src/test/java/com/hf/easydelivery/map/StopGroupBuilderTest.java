package com.hf.easydelivery.map;

import static org.junit.Assert.assertEquals;

import com.hf.easydelivery.dao.DeliveryInfo;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class StopGroupBuilderTest {

    private final StopGroupBuilder builder = new StopGroupBuilder();

    @Test
    public void nearbyParcelsAreGrouped() {
        DeliveryInfo a = parcel("A", 44.645000, -63.575000);
        DeliveryInfo b = parcel("B", 44.645090, -63.575000);
        List<StopGroup> groups = builder.build(Arrays.asList(a, b));
        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).size());
    }

    @Test
    public void fartherParcelsStaySeparated() {
        DeliveryInfo a = parcel("A", 44.645000, -63.575000);
        DeliveryInfo b = parcel("B", 44.646000, -63.575000);
        List<StopGroup> groups = builder.build(Arrays.asList(a, b));
        assertEquals(2, groups.size());
    }

    @Test
    public void chainExpansionDoesNotMergeBeyondRadius() {
        DeliveryInfo a = parcel("A", 44.645000, -63.575000);
        DeliveryInfo b = parcel("B", 44.645090, -63.575000);
        DeliveryInfo c = parcel("C", 44.645180, -63.575000);
        List<StopGroup> groups = builder.build(Arrays.asList(a, b, c));
        assertEquals(2, groups.size());
    }

    private DeliveryInfo parcel(String route, double lat, double lng) {
        DeliveryInfo info = new DeliveryInfo();
        info.setRouteNumber(route);
        info.setOrderSn(route);
        info.setLatitude(lat);
        info.setLongitude(lng);
        return info;
    }
}
