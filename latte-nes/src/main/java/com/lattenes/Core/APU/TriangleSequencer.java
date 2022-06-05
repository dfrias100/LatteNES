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

public class TriangleSequencer extends Sequencer {
    final byte[] triangleWave = {
        15, 14, 13, 12, 11, 10, 9, 
        8, 7, 6, 5, 4, 3, 2, 1, 0,
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15
    };

    LinearCounter linearCounter;
    ChannelLengthCounter lengthCounter; 

    @Override
    void manipulateSequence() {
        if (linearCounter.timer > 0 && lengthCounter.counter > 0) {
            sequence = (sequence + 1) % 32;
        }
    }
}
