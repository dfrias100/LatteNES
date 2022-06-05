package com.lattenes.Core.APU;

public class NESFilters {
    static LowPassFilter lowPassFilter1;
    static HighPassFilter highPassFilter1;
    static HighPassFilter highPassFilter2;

    public static void init() {
        lowPassFilter1 = new LowPassFilter(44100, 14000);
        highPassFilter1 = new HighPassFilter(44100, 90);
        highPassFilter2 = new HighPassFilter(44100, 440);
    }

    public static float filter(float signal) {
        signal = highPassFilter1.filter(signal);
        signal = highPassFilter2.filter(signal);
        signal = lowPassFilter1.filter(signal);
        return signal;
    }
}
