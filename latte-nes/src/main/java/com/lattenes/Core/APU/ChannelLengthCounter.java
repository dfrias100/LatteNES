package com.lattenes.Core.APU;

public class ChannelLengthCounter {
    boolean halt = false;
    short counter = 0;
    short clock(boolean enabled) {
        if (!enabled) {
            counter = 0;
        } else if (counter > 0 && !halt) {
            counter--;
        }
        return counter;
    }
}
