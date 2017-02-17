package org.sputnikdev.bluetooth.manager.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.gattparser.URL;

abstract class BluetoothObjectGovernor<T extends BluetoothObject> {

    private Logger logger = LoggerFactory.getLogger(BluetoothObjectGovernor.class);

    private final URL url;
    private T bluetoothObject;

    BluetoothObjectGovernor(URL url) {
        this.url = url;
    }

    T getBluetoothObject() {
        return bluetoothObject;
    }

    synchronized void update() {
        T bluetoothObject = getOrFindBluetoothObject();
        if (bluetoothObject == null) {
            return;
        }
        try {
            logger.info("Updating governor state: {}", getURL());
            updateState(bluetoothObject);
        } catch (Exception ex) {
            logger.info("Could not update governor state.", ex);
            reset();
        }
    }

    URL getURL() {
        return url;
    }

    void reset() {
        logger.info("Resetting governor: " + getURL());
        if (this.bluetoothObject != null) {
            disableNotifications(this.bluetoothObject);
        }
        this.bluetoothObject = null;
        logger.info("Governor has been reset: " + getURL());
    }

    abstract T findBluetoothObject();

    abstract void disableNotifications(T object);

    abstract void updateState(T object);

    abstract void init(T object);

    abstract void dispose();

    synchronized private T getOrFindBluetoothObject() {
        if (bluetoothObject == null) {
            this.bluetoothObject = findBluetoothObject();
            if (this.bluetoothObject != null) {
                init(this.bluetoothObject);
            }
        }
        return bluetoothObject;
    }

}
