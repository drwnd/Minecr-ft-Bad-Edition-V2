package com.MBEv2.test;

import com.MBEv2.core.*;

import static com.MBEv2.core.utils.Constants.*;

public class Launcher {

    private static WindowManager window;

    public static void main(String[] args) {
        EngineManager engine;
        window = new WindowManager(TITLE, 0, 0, true);
        engine = new EngineManager();

        System.out.println("Seed: " + SEED);

        try {
            engine.start();
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    public static WindowManager getWindow() {
        return window;
    }
}
