package com.example.liuliu.rsdglib;

import android.util.Log;

import java.util.ArrayList;
import java.util.PriorityQueue;

/**
 * Created by liuliu on 12/1/16.
 */

public class BatteryModel {
    private ArrayList<Double> model;
    private double cumulativeNoise;
    private boolean first;

    //DO NOT USE when battery info can be retrieved from API on certain devices
    public BatteryModel() {
        first = true;
        cumulativeNoise = 0;
        model = new ArrayList<Double>();
        model.add(374.281601);//100-99
        model.add(244.654263);
        model.add(310.5512004);
        model.add(340.517898);
        model.add(372.1541397);
        model.add(395.6428278);
        model.add(419.2834176);
        model.add(391.841846);
        model.add(364.7852223);
        model.add(364.7852223);//91-90
        //fake the other 90 percent
        for (int i = 0; i < 90; i++) {
            model.add(364.7852223);
        }
    }

    public double getEnergy(int start, int leng, boolean noise) {
        int s = 100 - start;
        double res = 0.0;
        for (int i = 0; i < leng; i++) {
            res += model.get(s + i);
        }
        if (noise) {
            if (first) {
                first = false;
                return res;
            }
            cumulativeNoise += 0.3 * model.get(s - 1);
            res -= cumulativeNoise;
        }
        Log.d("BatteryModel", "start:" + start + " leng:" + leng + " result:" + res + " Noise added:" + cumulativeNoise);
        return res;
    }
}
