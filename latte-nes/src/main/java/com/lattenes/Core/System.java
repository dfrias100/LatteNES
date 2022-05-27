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

import com.lattenes.Core.CPU.MOS6502;
import com.lattenes.Core.Cartridge.Cartridge;

public class System {
    private Memory memoryManagementUnit;
    private MOS6502 CPU;
    private PPU NESPPU;
    private Cartridge cartridge;
    private long systemCycleCount = 0;

    public System(String cartridgeFile) {
        Cartridge cartridge = new Cartridge(cartridgeFile);
        NESPPU = new PPU(cartridge);
        memoryManagementUnit = new Memory(cartridge, NESPPU);
        CPU = new MOS6502(memoryManagementUnit, true);
    }

    public boolean frameReady() {
        // return PPU.frameReady;
        return (systemCycleCount % 65535) == 0;
    }

    public void tick() {
        CPU.clock();
        systemCycleCount++;
    }

    public long getCycleCount() {
        return systemCycleCount;
    }

    public void endLog() {
        CPU.endLog();
    }

    public float[] getScreen() {
        return NESPPU.screen;
    }
}
