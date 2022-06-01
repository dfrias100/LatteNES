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
import java.util.Collections;
import java.io.*;

import com.lattenes.Core.Tuple;
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

    public Cartridge(String fileName) throws Exception {
        byte[] iNESHeader = new byte[16];

        try (InputStream inputStream = new FileInputStream(fileName)) {
            int byteRead = -1;
            int i = 0;

            while (i < 16 && (byteRead = inputStream.read()) != -1) {
                iNESHeader[i] = (byte) byteRead;
                i++;
            }
            i = 0;

            // Check if the file is an iNES file
            if (iNESHeader[0] != 0x4E || iNESHeader[1] != 0x45 || iNESHeader[2] != 0x53 || iNESHeader[3] != 0x1A) {
                System.out.println("File is not an iNES file");
                return;
            }

            byte prgSize = iNESHeader[4];
            byte chrSize = iNESHeader[5];
            byte flags6 = iNESHeader[6];
            byte flags7 = iNESHeader[7];
            //byte flags8 = iNESHeader[8];

            PRGBanks = (short) (prgSize & 0xFF);
            CHRBanks = (short) (chrSize & 0xFF);

            cartMirror = (flags6 & 0x01) == 0 ? Mirror.HORIZONTAL : Mirror.VERTICAL;
            boolean hasTrainer = (flags6 & 0x04) == 0x04;

            if (hasTrainer) {
                inputStream.skip(512);
            }

            mapperID = (short) ((flags7 & 0xF0) | (flags6 & 0xF0) >> 4);

            switch (mapperID) {
                case 0:
                    mapper = new Mapper0(PRGBanks, CHRBanks);
                    break;
                default:
                    throw (new Exception("Unsupported mapper: " + mapperID));
            }

            prgMEM = new ArrayList<Byte>(Collections.nCopies(PRGBanks * 0x4000, (byte) 0));
            chrMEM = new ArrayList<Byte>(Collections.nCopies(CHRBanks * 0x2000, (byte) 0));

            while (i < prgMEM.size() && (byteRead = inputStream.read()) != -1) {
                prgMEM.set(i, (byte) byteRead);
                i++;
            }

            i = 0;

            while (i < chrMEM.size() && (byteRead = inputStream.read()) != -1) {
                chrMEM.set(i, (byte) byteRead);
                i++;
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public boolean writeWordFromCPU(int address, byte value) {
        Tuple<Boolean, Integer> mapperWriteAttempt;

        if ((mapperWriteAttempt = mapper.writeWordFromCPU(address, value)).first) {
            prgMEM.set(mapperWriteAttempt.second, value);
            return true;
        }

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
        Tuple<Boolean, Integer> mapperWriteAttempt;

        if ((mapperWriteAttempt = mapper.writeWordFromPPU(address, value)).first) {
            chrMEM.set(mapperWriteAttempt.second, value);
            return true;
        }

        return false;
    }

    public Tuple<Boolean, Byte> readWordFromPPU(int address) {
        boolean readSuccessful = false;
        byte data = 0;
        Tuple<Boolean, Integer> mapperReadAttempt;

        if ((mapperReadAttempt = mapper.readWordFromPPU(address)).first) {
            readSuccessful = true;
            data = chrMEM.get(mapperReadAttempt.second);
        }

        return new Tuple<Boolean, Byte>(readSuccessful, data);
    }

    public void reset() {

    }

    public IMapper getMapper() {
        return mapper;
    }

    public Mirror getCartMirror() {
        if (mapper.getMirroring() == Mirror.HARDWARE) {
            return cartMirror;
        } else {
            return mapper.getMirroring();
        }
    }
}
