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

public abstract class Sequencer {
    long sequence = 0;
    long newSequence = 0;
    int timer = 0;
    int reload = 0;
    byte output = 0;

    byte clock(boolean enabled) {
        if (enabled) {
            timer--;
            if (timer < 0) {
                timer = reload + 1;
                manipulateSequence();
                output = (byte) ((sequence & 0x80) >> 7);
            }
        }
        return output;
    }

    abstract void manipulateSequence();
}
