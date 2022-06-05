/*
 * LatteNES: Nintendo Entertainment System (NES) Emulator written in Java
 * Copyright (C) 2022 Daniel Frias
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of  MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
