package com.MBEv2.test;

import com.MBEv2.core.*;

import static com.MBEv2.core.utils.Constants.*;

public class Launcher {

    private static WindowManager window;

    public static void main(String[] args) {
        window = new WindowManager(TITLE, 0, 0, true, true);
        EngineManager engine = new EngineManager();

        try {
            engine.init();

            engine.run();

            engine.cleanUp();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getClass());
            System.out.println(e.getMessage());

            FileManager.saveAllModifiedChunks();
            FileManager.saveGameState();
        }
    }

    public static WindowManager getWindow() {
        return window;
    }
}
