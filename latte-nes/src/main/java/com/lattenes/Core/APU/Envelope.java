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

public class Envelope {
    boolean enabled = false;
    boolean start = false;
    int volume = 0;
    int divider = 0;
    int decay = 0;
    int out = 0;

    void clock(boolean loop) {
        if (start) {
            start = false;
            decay = 15;
            divider = volume + 1;
        } else if (divider > 0) {
            divider--;
        } else {
            if (decay > 0) {
                decay--;
            } else if (loop) {
                decay = 15;
            }

            divider = volume + 1;
        }

        if (enabled) {
            out = decay;
        } else {
            out = volume;
        }
    }
}
