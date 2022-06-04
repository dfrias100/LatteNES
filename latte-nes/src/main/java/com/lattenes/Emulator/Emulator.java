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

import com.lattenes.Core.System;

public class Emulator {
    private EmulatorVideo video;
    private System NES;
    private long startFrame = 0;
    private EmulatorAudio audio;

    public Emulator() {
        video = new EmulatorVideo();
    }

    public boolean loadAndInit(String path) {
        try {
            NES = new System(path);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        video.system = NES;

        EmulatorInput.createTupleArray();
        EmulatorInput.initKeys();
        EmulatorInput.attachMMU(NES.getMemory());

        audio = new EmulatorAudio(44100);
        NES.attachEmuAudioObject(audio);

        return true;
    }

    private void capFrameRate(double fps) {
        double expectedFrametime = 1e9 / fps;
        long expectedFinishTime = startFrame + (long) expectedFrametime;
        while (java.lang.System.nanoTime() < expectedFinishTime) {}
    }

    public void run() {
        video.init();
        video.createTexture(NES.getScreen());
        double startTime = 0.0, endTime = 0.0;
        boolean keepTicking = false;

        startTime = video.getTime();
        while (!video.shouldClose()) {
            startFrame = java.lang.System.nanoTime();

            keepTicking = audio.bufHasLT(1000);

            do {
                NES.tick();
            } while (!NES.frameReady());

            audio.flushSamples(true);

            video.updateTexture(NES.getScreen());
            NES.clearFrameReady();
            video.draw();
            if (!keepTicking)
                capFrameRate(60.0988);
        }
        endTime = video.getTime();

        long CPUclocks = NES.getCycleCount() / 3;
        double elapsedTime = endTime - startTime;
        double MHz = CPUclocks / (elapsedTime * 1000000.0);
        java.lang.System.out.println("CPU MHz: " + MHz);

        NES.endLog();
        
        video.cleanup();
    }
}
