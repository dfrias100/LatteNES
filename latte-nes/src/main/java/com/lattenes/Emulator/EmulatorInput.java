package com.lattenes.Emulator;

import static org.lwjgl.glfw.GLFW.*;

import java.lang.reflect.Array;

import com.lattenes.Core.Memory;
import com.lattenes.Core.Tuple;

public class EmulatorInput {
    static int[] keys = new int[GLFW_KEY_LAST];
    static Tuple<Integer, Integer>[] mappedKeys;
    static Memory memory;

    static void attachMMU(Memory memory) {
        EmulatorInput.memory = memory;
    }

    @SuppressWarnings("unchecked")
    static void createTupleArray() {
        mappedKeys = (Tuple<Integer, Integer>[]) Array.newInstance(Tuple.class, 8);
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
    }

    static void keyboardInputCallback (long window, int key, int scancode, int action, int mods) {
        keys[key] = action;
        updateControllerState();
    }

    static void updateControllerState() {
        for (Tuple<Integer, Integer> key : mappedKeys) {
            if (keys[key.first] == GLFW_PRESS) {
                memory.controller1 |= key.second; 
            } else if (keys[key.first] == GLFW_RELEASE) {
                memory.controller1 &= ~key.second;
            }
        }
    }
}
