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
import com.lattenes.Core.Cartridge.Tuple;

public interface IMapper {
    // Read and writing methods return a tuple
    // The first element denotes if the operation was successful
    // The second element is the newly mapped address

    public Tuple<Boolean, Integer> writeWordFromCPU(int address, byte value);
    public Tuple<Boolean, Integer> writeWordFromPPU(int address, byte value);

    public Tuple<Boolean, Integer> readWordFromCPU(int address);
    public Tuple<Boolean, Integer> readWordFromPPU(int address);

    public void reset();

    public Mirror getMirroring();

    public boolean getIRQ();
    public void clearIRQ();
}