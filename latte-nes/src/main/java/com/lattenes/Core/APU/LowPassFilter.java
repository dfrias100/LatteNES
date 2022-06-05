package com.lattenes.Core.APU;

public class LowPassFilter {
    float b0;
    float b1;
    float a1;
    float prevT;
    float prevY;

    public LowPassFilter(float sampleRate, float cutoff) {
        float c = sampleRate / (float) Math.PI / cutoff;
        float a0i = 1.0f / (1.0f + c);

        b0 = a0i;
        b1 = a0i;
        a1 = (1.0f - c) * a0i;
        prevT = 0.0f;
        prevY = 0.0f;
    }

    public float filter(float signal) {
        float y = b0 * signal + b1 * prevT - a1 * prevY;
        prevT = signal;
        prevY = y;
        return y;
    }
}
