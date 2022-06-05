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

package com.lattenes.Emulator;

import static org.lwjgl.glfw.GLFW.*;

import java.lang.reflect.Array;

import com.lattenes.Core.Memory;
import com.lattenes.Util.Tuple;

public class EmulatorInput {
    static int[] keys = new int[GLFW_KEY_LAST];
    static Tuple<Integer, Integer>[] mappedKeys;
    static Memory memory;

    static void attachMMU(Memory memory) {
        EmulatorInput.memory = memory;
    }

    @SuppressWarnings("unchecked")
    static void createTupleArray() {
        mappedKeys = (Tuple<Integer, Integer>[]) Array.newInstance(Tuple.class, 10);
    }

    static void initKeys() {
        mappedKeys[0] = new Tuple<Integer, Integer>(GLFW_KEY_Z, 0x40);
        mappedKeys[1] = new Tuple<Integer, Integer>(GLFW_KEY_X, 0x80);
        mappedKeys[2] = new Tuple<Integer, Integer>(GLFW_KEY_ENTER, 0x10);
        mappedKeys[3] = new Tuple<Integer, Integer>(GLFW_KEY_RIGHT_SHIFT, 0x20);
        mappedKeys[4] = new Tuple<Integer, Integer>(GLFW_KEY_UP, 0x08);
        mappedKeys[5] = new Tuple<Integer, Integer>(GLFW_KEY_DOWN, 0x04);
        mappedKeys[6] = new Tuple<Integer, Integer>(GLFW_KEY_LEFT, 0x02);
        mappedKeys[7] = new Tuple<Integer, Integer>(GLFW_KEY_RIGHT, 0x01);
        mappedKeys[8] = new Tuple<Integer, Integer>(GLFW_KEY_F1, 0x100);
        mappedKeys[9] = new Tuple<Integer, Integer>(GLFW_KEY_F2, 0x200);
    }

    static void keyboardInputCallback (long window, int key, int scancode, int action, int mods) {
        keys[key] = action;
        updateControllerState();
    }

    static void updateControllerState() {
        for (Tuple<Integer, Integer> key : mappedKeys) {
            if (keys[key.first] == GLFW_PRESS) {
                if (key.second >= 0x100) {
                    if (key.second == 0x100) {
                        // Save state
                        java.lang.System.out.println("Saving state");
                        memory.saveStateFlag = true;
                    } else if (key.second == 0x200) {
                        // Load state
                        java.lang.System.out.println("Loading state");
                        memory.loadStateFlag = true;
                    }
                } else {
                    memory.controller1 |= key.second; 
                }
            } else if (keys[key.first] == GLFW_RELEASE) {
                memory.controller1 &= ~key.second;
            }
        }
    }
}
