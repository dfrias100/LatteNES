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

package com.lattenes.Core.Cartridge.Mapper;

import com.lattenes.Core.Cartridge.Mirror;
import com.lattenes.Util.Tuple;

public class Mapper0 implements IMapper {
    private int PRGBanks;
    private int CHRBanks;

    public Mapper0(int PRGBanks, int CHRBanks) {
        this.PRGBanks = PRGBanks;
        this.CHRBanks = CHRBanks;
    }

    @Override
    public void reset() {
        // Nothing to do here
    }

    @Override
    public Tuple<Boolean, Integer> writeWordFromCPU(int address, byte value) {
        // Mapper 0 has no RAM, so it cannot be written to
        // Return false, with no address offset
        return new Tuple<Boolean, Integer>(false, 0);
    }

    @Override
    public Tuple<Boolean, Integer> writeWordFromPPU(int address, byte value) {
        // Mapper 0 can have CHR RAM but it is determined by the number of CHR banks
        // if the iNES header indicated 0 CHR banks, then the CHR RAM is enabled
        // otherwise it is disabled and RAM cannot be written to
        int newAddress = 0;
        boolean writeSuccess = false;
        if (address >= 0x0000 && address <= 0x1FFF && CHRBanks == 0) {
            // CHR RAM is enabled
            newAddress = address;
            writeSuccess = true;
        }

        return new Tuple<Boolean, Integer>(writeSuccess, newAddress);
    }

    @Override
    public Tuple<Boolean, Integer> readWordFromCPU(int address) {
        int newAddress = 0;
        boolean readSuccessful = false;
        if (address >= 0x8000 && address <= 0xFFFF) {
            // The offset depends on the number of PRG-ROM banks
            newAddress = address & (PRGBanks == 1 ? 0x3FFF : 0x7FFF);
            readSuccessful = true;
        }

        return new Tuple<Boolean, Integer>(readSuccessful, newAddress);
    }

    @Override
    public Tuple<Boolean, Integer> readWordFromPPU(int address) {
        int newAddress = 0;
        boolean readSuccessful = false;

        if (address >= 0x0000 && address <= 0x1FFF) {
            // Mapper 0 has just 1 CHR-ROM bank
            newAddress = address;
            readSuccessful = true;
        }

        return new Tuple<Boolean, Integer>(readSuccessful, newAddress);
    }

    @Override
    public boolean getIRQ() {
        // Mapper 0 has no ability to call IRQ
        return false;
    }

    @Override
    public void clearIRQ() {
        // No need to clear IRQ here
    }

    @Override
    public Mirror getMirroring() {
        // Mapper 0 can't dynamically change the mirroring
        return Mirror.HARDWARE;
    }
}
