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

package com.lattenes.Core.Cartridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.io.*;

import com.lattenes.Core.Cartridge.Mapper.IMapper;
import com.lattenes.Core.Cartridge.Mapper.Mapper0;

public class Cartridge {
    private ArrayList<Byte> prgMEM;
    private ArrayList<Byte> chrMEM;

    private short mapperID;
    private short PRGBanks;
    private short CHRBanks;

    private IMapper mapper;

    private Mirror cartMirror;

    public Cartridge(String fileName) {
        prgMEM = new ArrayList<Byte>(Collections.nCopies(0x4000, (byte) 0));
        chrMEM = new ArrayList<Byte>(Collections.nCopies(0x2000, (byte) 0));

        final String testRom = "nestest.nes";

        try (InputStream inputStream = new FileInputStream(testRom)) {
            int byteRead = -1;
            inputStream.skip(16);
            int i = 0;

            while ((byteRead = inputStream.read()) != -1 && i < prgMEM.size()) {
                prgMEM.set(i, (byte) byteRead);
                i++;
            }

            i = 0;

            while ((byteRead = inputStream.read()) != -1 && i < chrMEM.size()) {
                chrMEM.set(i, (byte) byteRead);
                i++;
            }
        } catch(IOException e) {
            e.printStackTrace();
        }

        mapper = new Mapper0(1, 1);
    }

    public boolean writeWordFromCPU(int address, byte value) {
        return false;
    }

    public Tuple<Boolean, Byte> readWordFromCPU(int address) {
        boolean readSuccessful = false;
        byte data = 0;
        Tuple<Boolean, Integer> mapperReadAttempt;

        if ((mapperReadAttempt = mapper.readWordFromCPU(address)).first) {
            readSuccessful = true;
            data = prgMEM.get(mapperReadAttempt.second);
        }

        return new Tuple<Boolean, Byte>(readSuccessful, data);
    }

    public boolean writeWordFromPPU(int address, byte value) {
        return false;
    }

    public Tuple<Boolean, Byte> readWordFromPPU(int address) {
        boolean readSuccessful = false;
        byte data = 0;
        Tuple<Boolean, Integer> mapperReadAttempt;

        if ((mapperReadAttempt = mapper.readWordFromPPU(address)).first) {
            readSuccessful = true;
            data = prgMEM.get(mapperReadAttempt.second);
        }

        return new Tuple<Boolean, Byte>(readSuccessful, data);
    }

    public void reset() {

    }

    public IMapper getMapper() {
        return mapper;
    }

    public Mirror getCartMirror() {
        // This is wrong, but it's a placeholder for now
        return cartMirror;
    }
}
