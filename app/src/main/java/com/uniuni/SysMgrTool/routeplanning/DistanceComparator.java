package com.uniuni.SysMgrTool.routeplanning;

import java.util.Comparator;

public class DistanceComparator implements Comparator<Place> {
    @Override
    public int compare(Place o1, Place o2) {
        double d = o1.getDistance() - o2.getDistance();
        if (d == 0)
            return 0;
        else if (d > 0.0000001)
            return 1;
        else
            return -1;
    }
}
