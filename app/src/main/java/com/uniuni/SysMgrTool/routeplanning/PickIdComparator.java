package com.uniuni.SysMgrTool.routeplanning;

import java.util.Comparator;

public class PickIdComparator implements Comparator<Place> {

    @Override
    public int compare(Place o1, Place o2) {
        return o1.getPickId() - o2.getPickId();
    }
}
