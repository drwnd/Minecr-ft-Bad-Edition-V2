package com.MBEv2.core;

import com.MBEv2.core.entity.GUIElement;
import com.MBEv2.core.entity.Player;
import com.MBEv2.test.Launcher;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

public class MouseInput {

    private final Vector2f previousPos, currentPos;
    private Vector2f displayVec;
    private final Player player;

    private long rightButtonPressTime = -1, leftButtonPressTime = -1;
    private boolean rightButtonWasJustPressed, leftButtonWasJustPressed;
    private boolean mouseButton4IsPressed, mouseButton5IsPressed;

    public MouseInput(Player player) {
        this.player = player;
        previousPos = new Vector2f(0, 0);
        currentPos = new Vector2f(0, 0);
        displayVec = new Vector2f();
    }

    public void init() {
        GLFW.glfwSetCursorPosCallback(Launcher.getWindow().getWindow(), (window, xPos, yPos) -> {
            currentPos.x = (float) xPos;
            currentPos.y = (float) yPos;
        });

        GLFW.glfwSetMouseButtonCallback(Launcher.getWindow().getWindow(), (window, button, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
                    leftButtonPressTime = System.nanoTime();
                    leftButtonWasJustPressed = true;
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
                    rightButtonPressTime = System.nanoTime();
                    rightButtonWasJustPressed = true;
                }
            } else if (action == GLFW.GLFW_RELEASE) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
                    leftButtonPressTime = -1;
                    leftButtonWasJustPressed = false;
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
                    rightButtonPressTime = -1;
                    rightButtonWasJustPressed = false;
                }
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_5)
                mouseButton5IsPressed = action == GLFW.GLFW_PRESS;
            if (button == GLFW.GLFW_MOUSE_BUTTON_4)
                mouseButton4IsPressed = action == GLFW.GLFW_PRESS;
        });

        GLFW.glfwSetScrollCallback(Launcher.getWindow().getWindow(), (window, xPos, yPos) -> {
            if (!player.isInInventory())
                return;
            ArrayList<GUIElement> inventoryElements = player.getInventoryElements();
            float scrollValue = (float) yPos * -0.05f;
            player.addToInventoryScroll(scrollValue);
            for (GUIElement element : inventoryElements) {
                element.getPosition().add(0.0f, scrollValue);
            }
        });

        GLFW.glfwSetInputMode(Launcher.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }

    public void input() {
        float x = currentPos.x - previousPos.x;
        float y = currentPos.y - previousPos.y;

        displayVec.y = x;
        displayVec.x = y;

        previousPos.x = currentPos.x;
        previousPos.y = currentPos.y;
    }

    public Vector2f getDisplayVec() {
        Vector2f returns = displayVec;
        displayVec = new Vector2f();
        return returns;
    }

    public long getRightButtonPressTime() {
        return rightButtonPressTime;
    }

    public long getLeftButtonPressTime() {
        return leftButtonPressTime;
    }

    public boolean wasRightButtonJustPressed() {
        boolean returnValue = rightButtonWasJustPressed;
        rightButtonWasJustPressed = false;
        return returnValue;
    }

    public boolean wasLeftButtonJustPressed() {
        boolean returnValue = leftButtonWasJustPressed;
        leftButtonWasJustPressed = false;
        return returnValue;
    }

    public boolean isMouseButton4IsPressed() {
        return mouseButton4IsPressed;
    }

    public boolean isMouseButton5IsPressed() {
        return mouseButton5IsPressed;
    }
}
