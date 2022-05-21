/*
 * Nintendo Entertainment System (NES) Emulator written in Java
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

import java.util.Random;

import com.lattenes.Core.Memory.Memory;;

public class Emulator {
    private EmulatorVideo video;
    private Memory memory;

    public Emulator() {
        video = new EmulatorVideo();
        memory = new Memory();
    }

    public void run() {
        video.init();

        // Dummy texture, just to show something
        // In reality, we would pass the texture from the PPU
        float[] framebuffer = new float[240 * 256 * 4];
        Random random = new Random();

        for (int i = 0; i < 240; i++) {
            for (int j = 0; j < 256; j++) {
                int idx = i * 256 * 4 + j * 4;
                framebuffer[idx + 0] = random.nextFloat();
                framebuffer[idx + 1] = random.nextFloat();
                framebuffer[idx + 2] = random.nextFloat();
                framebuffer[idx + 3] = 0.0f;
            }
        }

        video.createTexture(framebuffer);

        while (!video.shouldClose()) {
            // The PPU would signal a draw update if the frame is ready,
            // for now we have an idle loop
            video.draw();

            // Ideally, we would tick all of the components here, but
            // they are yet to be implemented            
            for (int i = 0; i < 240; i++) {
                for (int j = 0; j < 256; j++) {
                    int idx = i * 256 * 4 + j * 4;
                    framebuffer[idx + 0] = random.nextFloat();
                    framebuffer[idx + 1] = random.nextFloat();
                    framebuffer[idx + 2] = random.nextFloat();
                    framebuffer[idx + 3] = 0.0f;
                }
            }

            // This would probably go in an if statement where the draw call would
            // be, but for now we just update the random texture
            video.updateTexture(framebuffer);
        }
        
        video.cleanup();
    }
}
