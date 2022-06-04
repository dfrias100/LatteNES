package com.lattenes.Core.APU;

public class NoiseSequcencer extends Sequencer {
    boolean mode = false;
    @Override
    void manipulateSequence() {
        int feedback;

        if (mode) {
            feedback = (int) ((sequence & 0x01) ^ ((sequence >> 6) & 0x01));
        } else {
            feedback = (int) ((sequence & 0x01) ^ ((sequence >> 1) & 0x01));
        }

        sequence = (sequence >> 1) | (feedback << 14);
    }
}
