package com.uniuni.SysMgrTool.routeplanning;

import java.util.ArrayList;
import java.util.List;

public class Place implements Cloneable {
    private static final int TOP_N = 1;
    private static final int MAX_GAP = 4;
    private static final double MAX_MILES = 0.01f;

    private String tid;
    private Short pickId;
    private Double lon;
    private Double alon;
    private Double distance;
    private boolean bBigGap = false;


    private String address;


    private ArrayList<Place> otherPlaces = new ArrayList<>();

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public Short getPickId() {
        return pickId;
    }

    public void setPickId(Short pickId) {
        this.pickId = pickId;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public Double getAlon() {
        return alon;
    }

    public void setAlon(Double alon) {
        this.alon = alon;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public boolean isbBigGap() {
        return bBigGap;
    }

    public void setbBigGap(boolean bBigGap) {
        this.bBigGap = bBigGap;
    }

    @Override
     public Object clone() {
        Place p = null;
        try {
            p = (Place) super.clone();   //浅复制
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        return p;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void initOtherPlaces(List<Place> lstAll)
    {
        for (Place p : lstAll)
        {
            if (p.equals(this))
                continue;

            double d = distance(this.alon , this.lon , p.alon,p.lon);
            if (d > MAX_MILES)
                continue;

            p.distance = d;

            Place pp = (Place)p.clone();

            otherPlaces.add(p);
        }
    }

    public void judgeTop3NearstPaces()
    {
        DistanceComparator cmp = new DistanceComparator();
        otherPlaces.sort(cmp);

        if (otherPlaces.size() < TOP_N)
            return;

        ArrayList<Place> pickIds = new ArrayList<>();
        for (int i = 0; i < TOP_N; i++)
        {
            pickIds.add(otherPlaces.get(i));
        }

        pickIds.add(this);
        PickIdComparator pCmp = new PickIdComparator();
        pickIds.sort(pCmp);

        for(int i = 0; i < pickIds.size() - 1; i++)
        {
            Place p1 = pickIds.get(i);
            Place p2 = pickIds.get(i + 1);

            if (Math.abs(p1.getPickId() - p2.getPickId()) > MAX_GAP)
            {
                p1.setbBigGap(true);
                p2.setbBigGap(true);

                this.setbBigGap(true);
            }
        }
    }

    double distance(double lat1, double lon1, double lat2, double lon2)
    {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2))
                + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2))
                * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        double miles = dist * 60 * 1.1515;
        return miles;
    }

    public boolean checkDistance(double lat1, double lon1)
    {
        double m = distance(lat1,lon1,alon,lon);
        if (m > MAX_MILES)
            return true;
        else
            return false;
    }

    //将角度转换为弧度
    static double deg2rad(double degree)
    {
        return degree / 180 * Math.PI;
    }
    //将弧度转换为角度
    static double rad2deg(double radian)
    {
        return radian * 180 / Math.PI;
    }

}

