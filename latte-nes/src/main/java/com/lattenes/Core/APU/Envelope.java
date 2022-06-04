package com.lattenes.Core.APU;

public class Envelope {
    boolean enabled = false;
    boolean start = false;
    int volume = 0;
    int divider = 0;
    int decay = 0;
    int out = 0;

    void clock(boolean loop) {
        if (!start) {
            if (divider == 0) {
                divider = volume;
                if (decay == 0) {
                    if (loop)
                        decay = 15;
                } else {
                    decay--;
                }
            } else {
                divider--;
            }
        } else {
            start = false;
            decay = 15;
            divider = volume;
        }

        if (enabled) {
            out = decay;
        } else {
            out = volume;
        }
    }
}
