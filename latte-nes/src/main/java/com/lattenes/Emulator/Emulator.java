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
        boolean keepTicking = false;

        while (!video.shouldClose()) {
            startFrame = java.lang.System.nanoTime();

            keepTicking = audio.bufHasLT(1468);

            do {
                NES.tick();
            } while (!NES.frameReady());

            audio.flushSamples(!keepTicking);

            video.updateTexture(NES.getScreen());
            NES.clearFrameReady();
            video.draw();
            if (!keepTicking)
                capFrameRate(60.0988);
        }

        NES.endLog();
        audio.destroy();
        video.cleanup();
    }
}
