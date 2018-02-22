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
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.ValueListener;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author Vlad Kolotov
 */
class CharacteristicGovernorImpl extends AbstractBluetoothObjectGovernor<Characteristic>
    implements CharacteristicGovernor {

    private Logger logger = LoggerFactory.getLogger(CharacteristicGovernorImpl.class);

    private List<ValueListener> valueListeners = new CopyOnWriteArrayList<>();
    private ValueNotification valueNotification;
    private boolean canNotify;
    private Instant lastNotified;

    CharacteristicGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        super(bluetoothManager, url);
    }

    @Override
    void init(Characteristic characteristic) {
        logger.debug("Initializing characteristic governor: {}", url);
        canNotify = canNotify(characteristic);
        logger.trace("Characteristic governor initialization performed: {} : {}", url, canNotify);
    }

    @Override
    void update(Characteristic characteristic) {
        logger.trace("Updating characteristic governor: {}", url);
        if (canNotify) {
            boolean notifying = characteristic.isNotifying();
            logger.trace("Updating characteristic governor notifications state: {} : {} / {} / {}",
                    url, valueListeners.isEmpty(), notifying, valueNotification == null);
            if (!valueListeners.isEmpty() && (!notifying || valueNotification == null)) {
                enableNotification(characteristic);
            } else if (valueListeners.isEmpty() && notifying) {
                disableNotification(characteristic);
            }
        }
    }

    @Override
    void reset(Characteristic characteristic) {
        logger.debug("Resetting characteristic governor: {}", url);
        valueNotification = null;
        try {
            if (canNotify && characteristic.isNotifying()) {
                characteristic.disableValueNotifications();
            }
        } catch (Exception ex) {
            logger.warn("Error occurred while resetting characteristic: {} : {} ", url, ex.getMessage());
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        logger.debug("Disposing characteristic governor: {}", url);
        valueListeners.clear();
        logger.trace("Characteristic governor disposed: {}", url);
    }

    @Override
    public void addValueListener(ValueListener valueListener) {
        valueListeners.add(valueListener);
    }

    @Override
    public void removeValueListener(ValueListener valueListener) {
        valueListeners.remove(valueListener);
    }

    @Override
    public Set<CharacteristicAccessType> getFlags() throws NotReadyException {
        return interact("getFlags", Characteristic::getFlags);
    }

    @Override
    public boolean isNotifiable() throws NotReadyException {
        Set<CharacteristicAccessType> flgs = getFlags();
        return flgs.contains(CharacteristicAccessType.NOTIFY) || flgs.contains(CharacteristicAccessType.INDICATE);
    }

    @Override
    public boolean isNotifying() throws NotReadyException {
        return isReady() && interact("isNotifying", Characteristic::isNotifying);
    }

    @Override
    public boolean isWritable() throws NotReadyException {
        Set<CharacteristicAccessType> flgs = getFlags();
        return flgs.contains(CharacteristicAccessType.WRITE)
            || flgs.contains(CharacteristicAccessType.WRITE_WITHOUT_RESPONSE);
    }

    @Override
    public boolean isReadable() throws NotReadyException {
        return getFlags().contains(CharacteristicAccessType.READ);
    }

    @Override
    public byte[] read() throws NotReadyException {
        return interact("read", Characteristic::readValue, true);
    }

    @Override
    public boolean write(byte[] data) throws NotReadyException {
        return interact("write", characteristic -> characteristic.writeValue(data), true);
    }

    @Override
    public String toString() {
        return "[Characteristic] " + getURL();
    }

    @Override
    public BluetoothObjectType getType() {
        return BluetoothObjectType.CHARACTERISTIC;
    }

    @Override
    public void accept(BluetoothObjectVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    @Override
    public Instant getLastNotified() {
        return lastNotified;
    }

    @Override
    void notifyLastChanged() {
        notifyLastChanged(BluetoothManagerUtils.max(getLastInteracted(), lastNotified));
    }

    private void updateLastNotified() {
        lastNotified = Instant.now();
    }

    private void enableNotification(Characteristic characteristic) {
        logger.debug("Enabling characteristic notifications: {} : {} / {}",
                getURL(), valueNotification == null, canNotify);
        if (valueNotification == null && canNotify) {
            ValueNotification notification = new ValueNotification();
            characteristic.enableValueNotifications(notification);
            valueNotification = notification;
        }
    }

    private void disableNotification(Characteristic characteristic) {
        logger.debug("Disabling characteristic notifications: {} : {} / {}",
                getURL(), valueNotification == null, canNotify);
        ValueNotification notification = valueNotification;
        valueNotification = null;
        if (notification != null && canNotify) {
            characteristic.disableValueNotifications();
        }
    }

    private static boolean canNotify(Characteristic characteristic) {
        Set<CharacteristicAccessType> flgs = characteristic.getFlags();
        return flgs.contains(CharacteristicAccessType.NOTIFY) || flgs.contains(CharacteristicAccessType.INDICATE);
    }

    private class ValueNotification implements Notification<byte[]> {
        @Override
        public void notify(byte[] data) {
            logger.trace("Characteristic value changed (notification): {}", url);
            updateLastInteracted();
            updateLastNotified();
            BluetoothManagerUtils.safeForEachError(valueListeners, listener -> listener.changed(data), logger,
                    "Execution error of a characteristic listener");
        }
    }

}
