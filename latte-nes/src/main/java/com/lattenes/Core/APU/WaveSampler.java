package com.lattenes.Core.APU;

public class WaveSampler {
    double freq = 0;
    double duty = 0;
    double amp = 1;
    double pi = 3.14159;
    double harmonics = 20;

    private double approxSin(double t) {
        double j = t * 0.15915f;
        j = j - (int) j;
        return 20.785 * j * (j - 0.5) * (j - 1.0);
    }

    double sample(double t) {
        double a = 0;
        double b = 0;
        double p = duty * 2.0 * pi;

        for (double n = 1; n < harmonics; n++) {
            double c = n * freq * 2.0 * pi * t;
            a += -approxSin(c) / n;
            b += -approxSin(c - p * n) / n;
        }

        return (2.0 * amp / pi) * (a - b);
    }
}
