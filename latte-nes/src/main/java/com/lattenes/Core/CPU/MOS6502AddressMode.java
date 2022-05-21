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

package com.lattenes.Core.CPU;

public enum MOS6502AddressMode {
    ACC, // Accumulator
    ABS, // Absolute
    ABX, // Absolute with X offset
    ABY, // Absolute with Y offset
    IMM, // Immediate
    IMP, // Implied
    IND, // Indirect
    INX, // Indirect with X offset
    INY, // Indirect with Y offset
    REL, // Relative
    ZPG, // Zero Page
    ZPX, // Zero Page with X offset
    ZPY  // Zero Page with Y offset

    // More details can be found within the MOS6502.java file
}
