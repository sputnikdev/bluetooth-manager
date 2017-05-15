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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.BluetoothSmartDeviceListener;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.GattCharacteristic;
import org.sputnikdev.bluetooth.manager.GattService;
import org.sputnikdev.bluetooth.manager.GenericBluetoothDeviceListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;

/**
 *
 * @author Vlad Kolotov
 */
class DeviceGovernorImpl extends BluetoothObjectGovernor<Device> implements DeviceGovernor {

    private Logger logger = LoggerFactory.getLogger(DeviceGovernorImpl.class);

    private final List<GenericBluetoothDeviceListener> genericBluetoothDeviceListener = new ArrayList<>();
    private final List<BluetoothSmartDeviceListener> bluetoothSmartDeviceListeners = new ArrayList<>();
    private ConnectionNotification connectionNotification;
    private BlockedNotification blockedNotification;
    private ServicesResolvedNotification servicesResolvedNotification;
    private RSSINotification rssiNotification;
    private boolean connectionControl;
    private boolean blockedControl;
    private boolean online = false;
    private String alias;

    DeviceGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        super(bluetoothManager, url);
    }

    @Override
    void init(Device device) {
        enableRSSINotifications(device);
        enableConnectionNotifications(device);
        enableServicesResolvedNotifications(device);
        enableBlockedNotifications(device);
    }

    @Override
    void updateState(Device device) {
        boolean blocked = device.isBlocked();

        if (blockedControl != blocked) {
            device.setBlocked(blockedControl);
            blocked = blockedControl;
        }

        updateAlias(device);

        if (!blocked) {
            boolean connected = device.isConnected();
            if (connectionControl && !connected) {
                connected = device.connect();
            } else if (!connectionControl && connected) {
                device.disconnect();
                resetCharacteristics();
                connected = false;
            }
            if (connected) {
                updateOnline(true);
                updateCharacteristics();
                updateLastUpdated();
            } else {
                boolean inRange = isOnline();
                updateOnline(inRange);
            }
        }
    }
    @Override
    void disableNotifications(Device device) {
        logger.info("Disable device notifications: " + getURL());
        device.disableConnectedNotifications();
        device.disableServicesResolvedNotifications();
        device.disableRSSINotifications();
        device.disableBlockedNotifications();
        connectionNotification = null;
        servicesResolvedNotification = null;
    }

    @Override
    Device findBluetoothObject() {
        return BluetoothObjectFactory.getDefault().getDevice(getURL());
    }

    @Override
    void reset() {
        logger.info("Resetting device governor: " + getURL());

        updateOnline(false);
        resetCharacteristics();
        super.reset();

        logger.info("Device has been reset: " + getURL());
    }

    void dispose() {
        logger.info("Disposing device governor: " + getURL());
        try {
            Device device = getBluetoothObject();
            if (device != null && device.isConnected()) {
                logger.info("Disconnecting device: " + getURL());
                device.disconnect();
                notifyConnected(false);
            }
        } catch (Exception ex) {
            logger.warn("Could not disconnect device: " + getURL(), ex);
        }
        reset();
        synchronized (this.bluetoothSmartDeviceListeners) {
            this.bluetoothSmartDeviceListeners.clear();
        }
        synchronized (this.genericBluetoothDeviceListener) {
            this.genericBluetoothDeviceListener.clear();
        }
        logger.info("Device governor has been disposed: " + getURL());
    }

    @Override
    public int getBluetoothClass() throws NotReadyException {
        return getBluetoothObject().getBluetoothClass();
    }

    @Override
    public boolean isBleEnabled() throws NotReadyException {
        return getBluetoothClass() == 0;
    }

    @Override
    public String getName() throws NotReadyException {
        return getBluetoothObject().getName();
    }

    @Override
    public String getAliasControl() {
        return this.alias;
    }

    @Override
    public void setAliasControl(String alias) {
        this.alias = alias;
    }

    @Override
    public String getAlias() {
        return getBluetoothObject().getAlias();
    }

    @Override
    public String getDisplayName() throws NotReadyException {
        return this.alias != null ? this.alias : getName();
    }

    @Override
    public boolean getConnectionControl() {
        return connectionControl;
    }

    public void setConnectionControl(boolean connectionControl) {
        this.connectionControl = connectionControl;
    }

    @Override
    public boolean getBlockedControl() {
        return this.blockedControl;
    }

    @Override
    public void setBlockedControl(boolean blockedControl) {
        this.blockedControl = blockedControl;
    }

    @Override
    public boolean isConnected() throws NotReadyException {
        return getBluetoothObject().isConnected();
    }

    @Override
    public boolean isBlocked() throws NotReadyException {
        return getBluetoothObject().isBlocked();
    }

    @Override
    public boolean isOnline() {
        return Instant.now().minusSeconds(20).isBefore(this.getLastChanged().toInstant());
    }

    @Override
    public short getRSSI() throws NotReadyException {
        return getBluetoothObject().getRSSI();
    }

    @Override
    public void addBluetoothSmartDeviceListener(BluetoothSmartDeviceListener bluetoothSmartDeviceListener) {
        synchronized (this.bluetoothSmartDeviceListeners) {
            this.bluetoothSmartDeviceListeners.add(bluetoothSmartDeviceListener);
        }
    }

    @Override
    public void removeBluetoothSmartDeviceListener(BluetoothSmartDeviceListener bluetoothSmartDeviceListener) {
        synchronized (this.bluetoothSmartDeviceListeners) {
            this.bluetoothSmartDeviceListeners.remove(bluetoothSmartDeviceListener);
        }
    }

    @Override
    public void addGenericBluetoothDeviceListener(GenericBluetoothDeviceListener genericBluetoothDeviceListener) {
        synchronized (this.genericBluetoothDeviceListener) {
            this.genericBluetoothDeviceListener.remove(genericBluetoothDeviceListener);
        }
    }

    @Override
    public void removeGenericBluetoothDeviceListener(GenericBluetoothDeviceListener listener) {
        synchronized (this.genericBluetoothDeviceListener) {
            this.genericBluetoothDeviceListener.remove(listener);
        }
    }

    @Override
    public Map<URL, List<CharacteristicGovernor>> getServicesToCharacteristicsMap() throws NotReadyException {
        Map<URL, List<CharacteristicGovernor>> services = new HashMap<>();
        for (Service service : getBluetoothObject().getServices()) {
            URL serviceURL = service.getURL();
            services.put(serviceURL, (List) bluetoothManager.getGovernors(service.getCharacteristics()));
        }
        return services;
    }

    @Override
    public List<URL> getCharacteristics() throws NotReadyException {
        return BluetoothManagerUtils.getURLs(getAllCharacteristics());
    }

    @Override
    public List<CharacteristicGovernor> getCharacteristicGovernors() throws NotReadyException {
        return (List) bluetoothManager.getGovernors(getAllCharacteristics());
    }

    @Override
    public String toString() {
        String result = "[Device] " + getURL();
        if (isReady()) {
            result += " [" + getDisplayName() + "]";
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BluetoothGovernor that = (BluetoothGovernor) o;
        return url.equals(that.getURL());
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + url.hashCode();
        return result;
    }

    @Override
    public BluetoothObjectType getType() {
        return BluetoothObjectType.DEVICE;
    }

    @Override
    public void accept(BluetoothObjectVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    private List<Characteristic> getAllCharacteristics() throws NotReadyException {
        List<Characteristic> characteristics = new ArrayList<>();
        for (Service service : getBluetoothObject().getServices()) {
            characteristics.addAll(service.getCharacteristics());
        }
        return characteristics;
    }

    private void enableConnectionNotifications(Device bluetoothDevice) {
        if (this.connectionNotification == null) {
            logger.info("Enabling connection notification: {} ", getURL());
            this.connectionNotification = new ConnectionNotification();
            bluetoothDevice.enableConnectedNotifications(connectionNotification);
        }
    }

    private void enableBlockedNotifications(Device bluetoothDevice) {
        if (this.blockedNotification == null) {
            logger.info("Enabling blocked notification: {} ", getURL());
            this.blockedNotification = new BlockedNotification();
            bluetoothDevice.enableBlockedNotifications(blockedNotification);
        }
    }

    private void enableServicesResolvedNotifications(Device bluetoothDevice) {
        if (this.servicesResolvedNotification == null) {
            logger.info("Enabling services resolved notification: {} ", getURL());
            this.servicesResolvedNotification = new ServicesResolvedNotification();
            bluetoothDevice.enableServicesResolvedNotifications(this.servicesResolvedNotification);
        }
    }

    private void enableRSSINotifications(Device bluetoothDevice) {
        if (rssiNotification == null) {
            logger.info("Enabling RSSI notification: {} ", getURL());
            this.rssiNotification = new RSSINotification();
            bluetoothDevice.enableRSSINotifications(rssiNotification);
        }
    }

    private List<GattService> servicesResolved() {
        logger.info("Services resolved: " + getURL());
        List<GattService> services = new ArrayList<>();
        Device device = getBluetoothObject();
        for (Service service : device.getServices()) {
            List<GattCharacteristic> characteristics = new ArrayList<>();
            for (Characteristic characteristic : service.getCharacteristics()) {
                characteristics.add(convert(characteristic));
            }
            services.add(new GattService(service.getUUID(), characteristics));
        }
        return services;
    }

    private void updateCharacteristics() {
        bluetoothManager.updateDescendants(url);
    }

    private void resetCharacteristics() {
        bluetoothManager.resetDescendants(url);
    }

    private GattCharacteristic convert(Characteristic characteristic) {
        return new GattCharacteristic(characteristic.getUUID(), characteristic.getFlags());
    }

    private void notifyConnected(boolean connected) {
        synchronized (this.bluetoothSmartDeviceListeners) {
            for (BluetoothSmartDeviceListener listener : this.bluetoothSmartDeviceListeners) {
                try {
                    if (connected) {
                        listener.connected();
                    } else {
                        listener.disconnected();
                    }
                } catch (Exception ex) {
                    logger.error("Execution error of a connection listener", ex);
                }
            }
        }
    }

    private void notifyBlocked(boolean blocked) {
        synchronized (this.genericBluetoothDeviceListener) {
            for (GenericBluetoothDeviceListener listener : this.genericBluetoothDeviceListener) {
                try {
                    if (listener != null) {
                        listener.blocked(blocked);
                    }
                } catch (Exception ex) {
                    logger.error("Execution error of a Blocked listener", ex);
                }
            }
        }
    }

    private void notifyServicesResolved(boolean resolved) {
        synchronized (this.bluetoothSmartDeviceListeners) {
            for (BluetoothSmartDeviceListener listener : this.bluetoothSmartDeviceListeners) {
                try {
                    if (resolved) {
                        listener.servicesResolved(servicesResolved());
                    } else {
                        listener.servicesUnresolved();
                    }
                } catch (Exception ex) {
                    logger.error("Execution error of a service resolved listener", ex);
                }
            }
        }
    }

    private void notifyRSSIChanged(Short rssi) {
        synchronized (this.genericBluetoothDeviceListener) {
            for (GenericBluetoothDeviceListener listener : this.genericBluetoothDeviceListener) {
                try {
                    if (listener != null) {
                        listener.rssiChanged(rssi != null ? rssi : 0);
                    }
                } catch (Exception ex) {
                    logger.error("Execution error of a RSSI listener", ex);
                }
            }
        }
    }

    private void updateOnline(boolean online) {
        if (online != this.online) {
            notifyOnline(online);
        }
        this.online = online;
    }

    private void updateAlias(Device device) {
        if (this.alias == null) {
            this.alias = device.getAlias();
        } else if (!this.alias.equals(device.getAlias())) {
            device.setAlias(this.alias);
        }
    }

    private void notifyOnline(boolean online) {
        synchronized (this.genericBluetoothDeviceListener) {
            for (GenericBluetoothDeviceListener listener : this.genericBluetoothDeviceListener) {
                try {
                    if (listener != null) {
                        if (online) {
                            listener.online();
                        } else {
                            listener.offline();
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Execution error of an online listener", ex);
                }
            }
        }
    }

    private class ConnectionNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean connected) {
            logger.info("Connected (notification): " + getURL() + " " + connected);
            notifyConnected(connected);
            updateLastUpdated();
        }
    }

    private class BlockedNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean blocked) {
            logger.info("Blocked (notification): " + getURL() + " " + blocked);
            notifyBlocked(blocked);
            updateLastUpdated();
        }
    }

    private class ServicesResolvedNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean serviceResolved) {
            logger.info("Services resolved (notification): " + serviceResolved);

            if (serviceResolved) {
                updateCharacteristics();
            } else {
                logger.info("Resetting characteristic governors due to services unresolved event");
                resetCharacteristics();
            }

            notifyServicesResolved(serviceResolved);
            updateLastUpdated();
        }
    }

    private class RSSINotification implements Notification<Short> {
        @Override
        public void notify(Short rssi) {
            notifyRSSIChanged(rssi);
            updateLastUpdated();
        }
    }

}
