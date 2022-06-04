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
