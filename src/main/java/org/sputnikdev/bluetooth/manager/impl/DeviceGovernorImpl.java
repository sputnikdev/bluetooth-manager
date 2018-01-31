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
import org.sputnikdev.bluetooth.Filter;
import org.sputnikdev.bluetooth.RssiKalmanFilter;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.AdapterGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.BluetoothSmartDeviceListener;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.GattCharacteristic;
import org.sputnikdev.bluetooth.manager.GattService;
import org.sputnikdev.bluetooth.manager.GenericBluetoothDeviceListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.Device;
import org.sputnikdev.bluetooth.manager.transport.Notification;
import org.sputnikdev.bluetooth.manager.transport.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Vlad Kolotov
 */
class DeviceGovernorImpl extends AbstractBluetoothObjectGovernor<Device> implements DeviceGovernor {

    private Logger logger = LoggerFactory.getLogger(DeviceGovernorImpl.class);

    static final int DEFAULT_RSSI_REPORTING_RATE = 1000;
    static final int DEFAULT_ONLINE_TIMEOUT = 20;
    static final short DEFAULT_TX_POWER = -55;
    static final double DEFAULT_SIGNAL_PROPAGATION_EXPONENT = 4.0; // indoors

    private final List<GenericBluetoothDeviceListener> genericBluetoothDeviceListeners = new CopyOnWriteArrayList<>();
    private final List<BluetoothSmartDeviceListener> bluetoothSmartDeviceListeners = new CopyOnWriteArrayList<>();
    private ConnectionNotification connectionNotification;
    private BlockedNotification blockedNotification;
    private ServicesResolvedNotification servicesResolvedNotification;
    private RSSINotification rssiNotification;
    private ServiceDataNotification serviceDataNotification;
    private ManufacturerDataNotification manufacturerDataNotification;
    private boolean connectionControl;
    private boolean blockedControl;
    private boolean online;
    private int onlineTimeout = DEFAULT_ONLINE_TIMEOUT;

    private final Lock rssiUpdateLock = new ReentrantLock();
    private Filter<Short> rssiFilter = new RssiKalmanFilter();
    private boolean rssiFilteringEnabled = true;
    private long rssiReportingRate = DEFAULT_RSSI_REPORTING_RATE;
    private Date rssiLastNotified = new Date();
    private short measuredTxPower;
    private double signalPropagationExponent;
    private long lastAdvertised;

    DeviceGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        super(bluetoothManager, url);
    }

    @Override
    void init(Device device) {
        enableRSSINotifications(device);
        enableConnectionNotifications(device);
        enableServicesResolvedNotifications(device);
        enableBlockedNotifications(device);
        enableManufacturerDataNotifications(device);
        enableServiceDataNotifications(device);
    }

    @Override
    void update(Device device) {
        AdapterGovernor adapterGovernor = bluetoothManager.getAdapterGovernor(getURL());
        if (adapterGovernor != null && adapterGovernor.isReady() && adapterGovernor.isPowered()) {
            updateBlocked(device);
            if (!blockedControl) {
                // Note: BlueGiga and TinyB devices work in different way:
                // TinyB would have thrown an exception if the device was out of range (or turned off)
                // BlueGiga would not thrown any exception by now
                // therefore we need to check if BlueGiga device is still alive by querying the device RSSI
                // Further note: TinyB device when connected constantly returns the very last known RSSI
                boolean connected = updateConnected(device);
                if (connected) {
                    notifyRSSIChanged(getRSSI());
                    updateLastChanged();
                }
            }
        }
        updateOnline(isOnline());
    }

    @Override
    void reset(Device device) {
        logger.info("Resetting device governor: " + getURL());
        updateOnline(false);
        logger.info("Disable device notifications: " + getURL());
        device.disableConnectedNotifications();
        device.disableServicesResolvedNotifications();
        device.disableRSSINotifications();
        device.disableBlockedNotifications();
        device.disableServiceDataNotifications();
        device.disableManufacturerDataNotifications();
        logger.info("Disconnecting device: " + getURL());
        if (device.isConnected()) {
            device.disconnect();
            notifyConnected(false);
            resetCharacteristics();
        }
        connectionNotification = null;
        servicesResolvedNotification = null;
        rssiNotification = null;
        blockedNotification = null;
        logger.info("Resetting device governor completed: " + getURL());
    }

    @Override
    public void dispose() {
        super.dispose();
        genericBluetoothDeviceListeners.clear();
        bluetoothSmartDeviceListeners.clear();
    }

    @Override
    public int getBluetoothClass() throws NotReadyException {
        return getBluetoothObject().getBluetoothClass();
    }

    @Override
    public boolean isBleEnabled() throws NotReadyException {
        return getBluetoothObject().isBleEnabled();
    }

    @Override
    public String getName() throws NotReadyException {
        return getBluetoothObject().getName();
    }

    @Override
    public String getAlias() throws NotReadyException {
        return getBluetoothObject().getAlias();
    }

    @Override
    public void setAlias(String alias) throws NotReadyException {
        getBluetoothObject().setAlias(alias);
    }

    @Override
    public String getDisplayName() throws NotReadyException {
        String alias = getAlias();
        return alias != null ? alias : getName();
    }

    @Override
    public boolean getConnectionControl() {
        return connectionControl;
    }

    public void setConnectionControl(boolean connectionControl) {
        this.connectionControl = connectionControl;
        scheduleUpdate();
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
        Date lastActivity = getLastActivity();
        return lastActivity != null && Instant.now().minusSeconds(onlineTimeout)
            .isBefore(getLastActivity().toInstant());
    }

    @Override
    public int getOnlineTimeout() {
        return onlineTimeout;
    }

    @Override
    public void setOnlineTimeout(int onlineTimeout) {
        this.onlineTimeout = onlineTimeout;
    }

    @Override
    public short getRSSI() throws NotReadyException {
        return getBluetoothObject().getRSSI();
    }

    @Override
    public void setRssiFilter(Class<? extends Filter<Short>> filter) {
        rssiFilter = createFilter(filter);
    }

    @Override
    public Filter<Short> getRssiFilter() {
        return rssiFilter;
    }

    @Override
    public boolean isRssiFilteringEnabled() {
        return rssiFilteringEnabled;
    }

    @Override
    public void setRssiFilteringEnabled(boolean rssiFilteringEnabled) {
        this.rssiFilteringEnabled = rssiFilteringEnabled;
    }

    @Override
    public long getRssiReportingRate() {
        return rssiReportingRate;
    }

    @Override
    public long getLastAdvertised() {
        return lastAdvertised;
    }

    @Override
    public short getTxPower() {
        return getBluetoothObject().getTxPower();
    }

    @Override
    public short getMeasuredTxPower() {
        return measuredTxPower;
    }

    @Override
    public void setMeasuredTxPower(short txPower) {
        measuredTxPower = txPower;
    }

    @Override
    public double getSignalPropagationExponent() {
        return signalPropagationExponent;
    }

    @Override
    public void setSignalPropagationExponent(double signalPropagationExponent) {
        this.signalPropagationExponent = signalPropagationExponent;
    }

    @Override
    public double getEstimatedDistance() {
        short rssi = 0;
        if (rssiFilteringEnabled && rssiFilter != null) {
            rssi = rssiFilter.current();
        }
        if (rssi == 0 && isReady()) {
            rssi = getRSSI();
        }
        if (rssi == 0) {
            return 0;
        }
        return Math.pow(10d, ((double) getTxPowerInternal() - rssi) / (10 * getPropagationExponentInternal()));
    }

    @Override
    public URL getLocation() {
        return url.getAdapterURL();
    }

    @Override
    public void setRssiReportingRate(long rssiReportingRate) {
        this.rssiReportingRate = rssiReportingRate;
    }

    @Override
    public void addBluetoothSmartDeviceListener(BluetoothSmartDeviceListener bluetoothSmartDeviceListener) {
        bluetoothSmartDeviceListeners.add(bluetoothSmartDeviceListener);
    }

    @Override
    public void removeBluetoothSmartDeviceListener(BluetoothSmartDeviceListener bluetoothSmartDeviceListener) {
        bluetoothSmartDeviceListeners.remove(bluetoothSmartDeviceListener);
    }

    @Override
    public void addGenericBluetoothDeviceListener(GenericBluetoothDeviceListener genericBluetoothDeviceListener) {
        genericBluetoothDeviceListeners.add(genericBluetoothDeviceListener);
    }

    @Override
    public void removeGenericBluetoothDeviceListener(GenericBluetoothDeviceListener listener) {
        genericBluetoothDeviceListeners.remove(listener);
    }

    @Override
    public boolean isServicesResolved() throws NotReadyException {
        try {
            return getBluetoothObject().isServicesResolved();
        } catch (NotReadyException ignore) {
            return false;
        }
    }

    @Override
    public List<GattService> getResolvedServices() throws NotReadyException {
        List<GattService> services = new ArrayList<>();
        Device device = getBluetoothObject();
        for (Service service : device.getServices()) {
            List<GattCharacteristic> characteristics = new ArrayList<>();
            for (Characteristic characteristic : service.getCharacteristics()) {
                characteristics.add(convert(characteristic));
            }
            services.add(new GattService(service.getURL(), characteristics));
        }
        return services;
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
            String displayName = getDisplayName();
            if (displayName != null) {
                result += " [" + displayName + "]";
            }
            if (isBleEnabled()) {
                result += " [BLE]";
            }
        }
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

    @Override
    public Map<Short, byte[]> getManufacturerData() {
        return getBluetoothObject().getManufacturerData();
    }

    @Override
    public Map<String, byte[]> getServiceData() {
        return getBluetoothObject().getServiceData();
    }

    void notifyConnected(boolean connected) {
        bluetoothSmartDeviceListeners.forEach(listener -> {
            try {
                if (connected) {
                    listener.connected();
                } else {
                    listener.disconnected();
                }
            } catch (Exception ex) {
                logger.error("Execution error of a connection listener", ex);
            }
        });
    }

    void notifyBlocked(boolean blocked) {
        genericBluetoothDeviceListeners.forEach(listener -> {
            try {
                listener.blocked(blocked);
            } catch (Exception ex) {
                logger.error("Execution error of a Blocked listener", ex);
            }
        });
    }

    void notifyServicesResolved(boolean resolved) {
        logger.info("Services resolved {}: {}", resolved, getURL());
        bluetoothSmartDeviceListeners.forEach(listener -> {
            try {
                if (resolved) {
                    listener.servicesResolved(getResolvedServices());
                } else {
                    listener.servicesUnresolved();
                }
            } catch (Exception ex) {
                logger.error("Execution error of a service resolved listener", ex);
            }
        });
    }

    void updateRSSI(short next) {
        lastAdvertised = System.currentTimeMillis();
        Filter<Short> filter = rssiFilter;
        if (rssiUpdateLock.tryLock()) {
            try {
                if (filter != null && rssiFilteringEnabled) {
                    // devices can report RSSI too fast that we can't handle it, so we skip some readings
                    notifyRSSIChanged(filter.next(next));
                } else {
                    notifyRSSIChanged(next);
                }
            } finally {
                rssiUpdateLock.unlock();
            }
        }
    }

    void notifyRSSIChanged(short next) {
        if (rssiReportingRate == 0
            || System.currentTimeMillis() - rssiLastNotified.getTime() > rssiReportingRate) {
            genericBluetoothDeviceListeners.forEach(listener -> {
                try {
                    listener.rssiChanged(next);
                } catch (Exception ex) {
                    logger.error("Execution error of a RSSI listener", ex);
                }
            });
            rssiLastNotified = new Date();
        }
    }

    void notifyOnline(boolean online) {
        genericBluetoothDeviceListeners.forEach(listener -> {
            try {
                if (online) {
                    listener.online();
                } else {
                    listener.offline();
                }
            } catch (Exception ex) {
                logger.error("Execution error of an online listener", ex);
            }
        });
    }

    private List<Characteristic> getAllCharacteristics() throws NotReadyException {
        List<Characteristic> characteristics = new ArrayList<>();
        List<Service> services = getBluetoothObject().getServices();
        if (services != null) {
            for (Service service : services) {
                List<Characteristic> chars = service.getCharacteristics();
                if (chars != null) {
                    characteristics.addAll(chars);
                }
            }
        }
        return characteristics;
    }

    private void enableConnectionNotifications(Device bluetoothDevice) {
        if (connectionNotification == null) {
            logger.info("Enabling connection notification: {} ", getURL());
            connectionNotification = new ConnectionNotification();
            bluetoothDevice.enableConnectedNotifications(connectionNotification);
        }
    }

    private void enableBlockedNotifications(Device bluetoothDevice) {
        if (blockedNotification == null) {
            logger.info("Enabling blocked notification: {} ", getURL());
            blockedNotification = new BlockedNotification();
            bluetoothDevice.enableBlockedNotifications(blockedNotification);
        }
    }

    private void enableServicesResolvedNotifications(Device bluetoothDevice) {
        if (servicesResolvedNotification == null) {
            logger.info("Enabling services resolved notification: {} ", getURL());
            servicesResolvedNotification = new ServicesResolvedNotification();
            bluetoothDevice.enableServicesResolvedNotifications(servicesResolvedNotification);
        }
    }

    private void enableRSSINotifications(Device bluetoothDevice) {
        if (rssiNotification == null) {
            logger.info("Enabling RSSI notification: {} ", getURL());
            rssiNotification = new RSSINotification();
            bluetoothDevice.enableRSSINotifications(rssiNotification);
        }
    }

    private void enableServiceDataNotifications(Device bluetoothDevice) {
        if (serviceDataNotification == null) {
            logger.info("Enabling service data notification: {} ", getURL());
            serviceDataNotification = new ServiceDataNotification();
            bluetoothDevice.enableServiceDataNotifications(serviceDataNotification);
        }
    }

    private void enableManufacturerDataNotifications(Device bluetoothDevice) {
        if (manufacturerDataNotification == null) {
            logger.info("Enabling manufacturer data notification: {} ", getURL());
            manufacturerDataNotification = new ManufacturerDataNotification();
            bluetoothDevice.enableManufacturerDataNotifications(manufacturerDataNotification);
        }
    }

    private void updateCharacteristics() {
        bluetoothManager.updateDescendants(url);
    }

    private void resetCharacteristics() {
        bluetoothManager.resetDescendants(url);
    }

    private static GattCharacteristic convert(Characteristic characteristic) {
        return new GattCharacteristic(characteristic.getURL(), characteristic.getFlags());
    }

    private void updateOnline(boolean online) {
        if (online != this.online) {
            notifyOnline(online);
        }
        this.online = online;
    }


    private void updateBlocked(Device device) {
        if (blockedControl != device.isBlocked()) {
            device.setBlocked(blockedControl);
        }
    }

    private boolean updateConnected(Device device) {
        boolean connected = device.isConnected();
        if (connectionControl && !connected) {
            connected = device.connect();
        } else if (!connectionControl && connected) {
            device.disconnect();
            resetCharacteristics();
            connected = false;
        }
        return connected;
    }

    private short getTxPowerInternal() {
        short txPower = measuredTxPower;
        if (txPower == 0 && isReady()) {
            try {
                txPower = getTxPower();
            } catch (NotReadyException ignore) { /* do nothing */ }
        }
        if (txPower == 0) {
            txPower = DEFAULT_TX_POWER;
        }
        return txPower;
    }

    private double getPropagationExponentInternal() {
        double propagationExponent = signalPropagationExponent;
        if (propagationExponent == 0) {
            AdapterGovernor adapterGovernor = bluetoothManager.getAdapterGovernor(getURL());
            propagationExponent = adapterGovernor.getSignalPropagationExponent();
        }
        if (propagationExponent == 0) {
            propagationExponent = DEFAULT_SIGNAL_PROPAGATION_EXPONENT;
        }
        return propagationExponent;
    }

    private class ConnectionNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean connected) {
            logger.info("Connected (notification): " + getURL() + " " + connected);
            notifyConnected(connected);
            updateLastChanged();
        }
    }

    private class BlockedNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean blocked) {
            logger.info("Blocked (notification): " + getURL() + " " + blocked);
            notifyBlocked(blocked);
            updateLastChanged();
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
            updateLastChanged();
        }
    }

    private class RSSINotification implements Notification<Short> {
        @Override
        public void notify(Short rssi) {
            updateRSSI(rssi);
            updateLastChanged();
        }
    }

    private class ServiceDataNotification implements Notification<Map<String, byte[]>> {
        @Override
        public void notify(Map<String, byte[]> serviceData) {
            bluetoothSmartDeviceListeners.forEach(listener -> {
                try {
                    listener.serviceDataChanged(serviceData);
                } catch (Exception ex) {
                    logger.error("Execution error of a service data listener", ex);
                }
            });
            updateLastChanged();
        }
    }

    private class ManufacturerDataNotification implements Notification<Map<Short, byte[]>> {
        @Override
        public void notify(Map<Short, byte[]> manufacturerData) {
            bluetoothSmartDeviceListeners.forEach(listener -> {
                try {
                    listener.manufacturerDataChanged(manufacturerData);
                } catch (Exception ex) {
                    logger.error("Execution error of a manufacturer data listener", ex);
                }
            });
            updateLastChanged();
        }
    }

    protected Filter<Short> createFilter(Class<? extends Filter<Short>> filter) {
        try {
            return filter != null ? filter.newInstance() : null;
        }  catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

}
