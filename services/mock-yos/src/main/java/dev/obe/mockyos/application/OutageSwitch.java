package dev.obe.mockyos.application;

import org.springframework.stereotype.Component;

/** Lets a test or a person take this YOS down, to watch the HHS retry. */
@Component
public class OutageSwitch {

    private volatile boolean down;

    public boolean isDown() {
        return down;
    }

    public void set(boolean down) {
        this.down = down;
    }
}
