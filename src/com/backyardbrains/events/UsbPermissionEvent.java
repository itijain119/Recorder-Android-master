package com.backyardbrains.events;

/**
 * @author Tihomir Leka <ticapeca at gmail.com>
 */
public class UsbPermissionEvent {

    private final boolean granted;

    public UsbPermissionEvent(boolean granted) {
        this.granted = granted;
    }

    public boolean isGranted() {
        return granted;
    }
}
