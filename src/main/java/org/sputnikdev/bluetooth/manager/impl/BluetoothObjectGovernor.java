package org.sputnikdev.bluetooth.manager.impl;

/*-
 * #%L
 * org.sputnikdev:bluetooth-manager
 * %%
 * Copyright (C) 2017 Sputnik Dev
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;

/**
 *
 * @author Vlad Kolotov
 */
abstract class BluetoothObjectGovernor<T extends BluetoothObject> implements BluetoothGovernor {

    private Logger logger = LoggerFactory.getLogger(BluetoothObjectGovernor.class);

    protected final BluetoothManagerImpl bluetoothManager;
    protected final URL url;
    private T bluetoothObject;
    private Date lastActivity = new Date();
    private final List<GovernorListener> governorListeners = new ArrayList<>();

    BluetoothObjectGovernor(BluetoothManagerImpl bluetoothManager, URL url) {
        this.bluetoothManager = bluetoothManager;
        this.url = url;
    }

    T getBluetoothObject() throws NotReadyException {
        if (bluetoothObject == null) {
            throw new NotReadyException("Bluetooth object is not ready: " + url);
        }
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

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public boolean isReady() {
        try {
            return getBluetoothObject() != null;
        } catch (NotReadyException e) {
            return false;
        }
    }

    @Override
    public void addGovernorListener(GovernorListener listener) {
        synchronized (this.governorListeners) {
            this.governorListeners.add(listener);
        }
    }

    @Override
    public void removeGovernorListener(GovernorListener listener) {
        synchronized (this.governorListeners) {
            this.governorListeners.remove(listener);
        }
    }

    @Override
    public Date getLastChanged() {
        return lastActivity;
    }

    void updateLastUpdated() {
        this.lastActivity = new Date();
        notifyLastActivityChanged(this.lastActivity);
    }

    void reset() {
        logger.info("Resetting governor: " + getURL());
        if (this.bluetoothObject != null) {
            notifyReady(false);
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
                notifyReady(true);
            }
        }
        return bluetoothObject;
    }

    private void notifyReady(boolean ready) {
        synchronized (this.governorListeners) {
            for (GovernorListener listener : this.governorListeners) {
                try {
                    listener.ready(ready);
                } catch (Exception ex) {
                    logger.error("Execution error of a governor listener: ready", ex);
                }
            }
        }
    }

    private void notifyLastActivityChanged(Date date) {
        synchronized (this.governorListeners) {
            for (GovernorListener listener : this.governorListeners) {
                try {
                    listener.lastUpdatedChanged(date);
                } catch (Exception ex) {
                    logger.error("Execution error of a governor listener: last changed", ex);
                }
            }
        }
    }

}
