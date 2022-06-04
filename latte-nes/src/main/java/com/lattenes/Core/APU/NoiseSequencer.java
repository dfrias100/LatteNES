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

public class NoiseSequencer extends Sequencer {
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
