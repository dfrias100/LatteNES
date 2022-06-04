package com.lattenes.Core.APU;

public class FrequencySweeper {
    boolean mute = false;
    boolean enabled = false;
    boolean negate = false;
    boolean reload = false;
    int diff = 0;
    int period = 0;
    int shift = 0;
    int timer = 0;
    PulseSequencer pulseSeq;

    void track() {
        if (enabled) {
            diff = pulseSeq.reload >> shift;
            mute = (pulseSeq.reload < 8) || (pulseSeq.reload > 0x7FF);
        }
    }

    void clock(int whichComplement) {
        if (timer == 0 && enabled && shift > 0 && !mute) {
            if (pulseSeq.reload >= 8 && diff < 0x07FF) {
                if (negate) {
                    pulseSeq.reload -= diff - whichComplement;
                    pulseSeq.reload &= 0xFFFF;
                } else {
                    pulseSeq.reload += diff;
                    pulseSeq.reload &= 0xFFFF;
                }
            }
        }

        if (timer == 0 || reload) {
            timer = period;
            reload = false;
        } else {
            timer--;
        }

        mute = (pulseSeq.reload < 8) || (pulseSeq.reload > 0x7FF);
    }
}
