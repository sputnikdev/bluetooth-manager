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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObject;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A root class for all governors in the system. Defines lifecycle and error handling/recovery processes for governors.
 *
 * <p>The lifecycle is divided on the following stages:
 *
 * <p><ul>
 *     <li>Native object acquisition</li>
 *     <li>Error handling and recovery</li>
 *     <li>Governing/maintaining state of the bluetooth object</li>
 * </ul>
 *
 * <p>All the stages above are handled in {@link #update()} method. When invoked, this method tries to acquire
 * a native object for corresponding device first. When acquired, {@link #init(BluetoothObject)} method is called
 * to perform initialisation (setting initial state, subscribing to the object events), then all registered governor
 * listeners are notified that the governor is "ready" by triggering {@link GovernorListener#ready(boolean)} method.
 * Once initialised, the {@link #update()} method switches to the next stage - maintaining state of the bluetooth
 * object by invoking {@link #update(BluetoothObject)} method, this is where main manipulations with
 * the bluetooth object are happening. If any exception occurs in the {@link #update(BluetoothObject)} method,
 * the Error handling stage begins by triggering {@link #reset(BluetoothObject)} method, which must revert
 * internal state of the governor to the initial state - the Native object acquisition.
 *
 * <p>In short this looks like that:
 *
 * <p>The update method is called outside of the governor (by a separate thread):
 * governor.update();
 *
 * <p>then the following happens:
 *
 * {@link #init(BluetoothObject)}
 * {@link GovernorListener#ready(boolean)} with argument - true
 * {@link #update(BluetoothObject)}
 *
 * <p>if the {@link #update(BluetoothObject)} method throws any exception, then
 * {@link #reset(BluetoothObject)}
 * {@link GovernorListener#ready(boolean)} with argument - false
 * are invoked, which brings the governor to its initial state, where everything begins from the start.
 *
 * <p>In order to help to release resources (native objects) {@link #reset(BluetoothObject)} method is used,
 * which must release any acquired resources, disconnect and unsubscribe from notifications.
 * It is recommended to call this method when, for example, a program exists or in any similar cases.
 *
 * @author Vlad Kolotov
 */
abstract class BluetoothObjectGovernor<T extends BluetoothObject> implements BluetoothGovernor {

    private Logger logger = LoggerFactory.getLogger(BluetoothObjectGovernor.class);

    protected final BluetoothManagerImpl bluetoothManager;
    protected final URL url;
    private T bluetoothObject;
    private String transport;
    private Date lastActivity = new Date();
    private final List<GovernorListener> governorListeners = new CopyOnWriteArrayList<>();

    BluetoothObjectGovernor(BluetoothManagerImpl bluetoothManager, URL url) {
        this.bluetoothManager = bluetoothManager;
        this.url = url;
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public boolean isReady() {
        return bluetoothObject != null;
    }

    @Override
    public void addGovernorListener(GovernorListener listener) {
        governorListeners.add(listener);
    }

    @Override
    public void removeGovernorListener(GovernorListener listener) {
        governorListeners.remove(listener);
    }

    @Override
    public Date getLastActivity() {
        return lastActivity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BluetoothObjectGovernor that = (BluetoothObjectGovernor) o;
        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + url.hashCode();
        return result;
    }

    public String getTransport() {
        return transport;
    }

    abstract void init(T object);

    abstract void update(T object);

    abstract void reset(T object);

    T getBluetoothObject() throws NotReadyException {
        if (bluetoothObject == null) {
            throw new NotReadyException("Bluetooth object is not ready: " + url);
        }
        return bluetoothObject;
    }

    final void update() {
        T object = getOrFindBluetoothObject();
        if (object == null) {
            return;
        }
        try {
            logger.debug("Updating governor state: {}", url);
            update(object);
            updateLastChanged();
            notifyLastChanged();
        } catch (Exception ex) {
            logger.error("Could not update governor state.", ex);
            reset();
        }
    }

    final void reset() {
        logger.info("Resetting governor: {}", url);
        try {
            if (bluetoothObject != null) {
                reset(bluetoothObject);
                notifyReady(false);
                bluetoothObject.dispose();
            }
        } catch (Exception ex) {
            logger.debug("Could not reset governor {}: {}", url, ex.getMessage());
        }
        bluetoothObject = null;
        logger.info("Governor has been reset: {}", url);
    }

    void updateLastChanged() {
        lastActivity = new Date();
    }

    void notifyReady(boolean ready) {
        governorListeners.forEach(listener -> {
            try {
                listener.ready(ready);
            } catch (Exception ex) {
                logger.error("Execution error of a governor listener: ready", ex);
            }
        });
    }

    void notifyLastChanged() {
        governorListeners.forEach(listener -> {
            try {
                listener.lastUpdatedChanged(lastActivity);
            } catch (Exception ex) {
                logger.error("Execution error of a governor listener: last changed", ex);
            }
        });
    }

    private T getOrFindBluetoothObject() {
        if (bluetoothObject == null) {
            bluetoothObject = bluetoothManager.getBluetoothObject(
                transport != null ? url.copyWithProtocol(transport) : url);
            if (bluetoothObject != null) {
                // update internal cache so that next time acquiring "native" object will be faster
                transport = bluetoothObject.getURL().getProtocol();
                try {
                    init(bluetoothObject);
                    notifyReady(true);
                } catch (Exception ex) {
                    logger.info("Could not init governor {}: {}", url, ex.getMessage());
                    reset();
                }
            }
        }
        return bluetoothObject;
    }

}
