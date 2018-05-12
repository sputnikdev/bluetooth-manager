package org.sputnikdev.bluetooth.manager.impl;

import org.sputnikdev.bluetooth.manager.BluetoothGovernor;

/**
 *
 * @author Vlad Kolotov
 */
interface BluetoothObjectGovernor extends BluetoothGovernor {

    /**
     * Initializing the governor.
     */
    void init();

    /**
     * Updating the governor.
     */
    void update();

    /**
     * Objects may decide if they can be updated.
     * @return
     */
    boolean isUpdatable();

    /**
     * Resetting the governor to be reused later.
     */
    void reset();

    /**
     * Disposing the governor so that it cannot be reused.
     */
    void dispose();

}
