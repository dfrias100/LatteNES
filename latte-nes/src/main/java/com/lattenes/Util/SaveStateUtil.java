package com.lattenes.Util;

import java.io.FileOutputStream;
import java.io.FileInputStream;

public class SaveStateUtil {
    public static void saveState(String saveName, byte[] state) {
        try {
            FileOutputStream fos = new java.io.FileOutputStream(saveName);
            fos.write(state);
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] loadState(String saveName) {
        try {
            FileInputStream fos = new java.io.FileInputStream(saveName);
            byte[] state = new byte[fos.available()];
            fos.read(state);
            fos.close();
            return state;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
