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

package com.lattenes.Core;

import com.lattenes.Core.Cartridge.Cartridge;
import com.lattenes.Core.Cartridge.Tuple;

public class Memory {
    /* NES complete memory map
        0x0000-0x07FF - RAM 
        0x0800-0x0FFF - Mirror of RAM
        0x1000-0x17FF - Also a mirror RAM
        0x1800-0x1FFF - Last mirror of RAM
        0x2000-0x2007 - PPU registers
        0x2008-0x3FFF - Mirror of PPU registers (repeats every 8 bytes)
        0x4000-0x4017 - APU and I/O registers
        0x4018-0x401F - APU and I/O registers, normally disabled
        0x4020-0xFFFF - Cartridge space, PRG-ROM, CHR-ROM, and mapper registers  

        This class implements CPU ram only, with write and read methods. A separate
        PPU and APU class will be created to handle the PPU and APU registers.

        More information about the NES memory map can be found at:
        http://www.nesdev.com/wiki/CPU_memory_map

        Some term definitions:
        - Word: 8-bits (1 byte)
        - Double-word: 16-bits (2 bytes)
    */
    private static final int RAM_SIZE = 0x800;
    private byte[] CPUMemory;
    private Cartridge cartridge;
    private PPU NESPPU;

    public Memory(Cartridge cartridge, PPU NESPPU) {
        CPUMemory = new byte[RAM_SIZE];
        this.cartridge = cartridge;
        this.NESPPU = NESPPU;
    }

    public void writeWord(int address, byte value) {

        if (cartridge.writeWordFromCPU(address, value)) {
            // Nothing to do if the cartridge wrote the value
            return;
        } else if (address <= 0x1FFF) {
            CPUMemory[address & 0x07FF] = value;
        } else if (address <= 0x3FFF) {
            // PPU access
        } else if (address == 0x4015) {
            // APU status write
        } else if (address == 0x4016 || address == 0x4017) {
            // Controller read
        }
    }

    public int readWord(int address) {
        byte data = 0x00;
        Tuple<Boolean, Byte> result;
        if ((result = cartridge.readWordFromCPU(address)).first) {
            data = result.second;
        } else if (address <= 0x1FFF) {
            data = CPUMemory[address & 0x07FF];
        } else if (address <= 0x3FFF) {
            // PPU access
        } else if (address == 0x4015) {
            // APU status read
        } else if (address == 0x4016 || address == 0x4017) {
            // Controller read
        }

        return (data & 0xFF);
    }

    public boolean pollNMI() {
        return NESPPU.requestingNMI();
    }

    public void clearNMI() {
        NESPPU.clearNMI();
    }
}
