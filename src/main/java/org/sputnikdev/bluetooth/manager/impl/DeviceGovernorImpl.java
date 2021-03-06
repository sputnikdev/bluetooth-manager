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
import org.sputnikdev.bluetooth.AddressType;
import org.sputnikdev.bluetooth.AddressUtils;
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
import org.sputnikdev.bluetooth.manager.auth.AuthenticationProvider;
import org.sputnikdev.bluetooth.manager.auth.BluetoothAuthenticationException;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.Device;
import org.sputnikdev.bluetooth.manager.transport.Notification;
import org.sputnikdev.bluetooth.manager.transport.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 *
 * @author Vlad Kolotov
 */
class DeviceGovernorImpl extends AbstractBluetoothObjectGovernor<Device> implements DeviceGovernor {

    private Logger logger = LoggerFactory.getLogger(DeviceGovernorImpl.class);

    static final int DEFAULT_RSSI_REPORTING_RATE = 0;
    static final int DEFAULT_ONLINE_TIMEOUT = 30;
    static final int RUMP_UP_TIMEOUT = 60;
    static final short DEFAULT_TX_POWER = -55;
    static final double DEFAULT_SIGNAL_PROPAGATION_EXPONENT = 4.0; // indoors

    private final List<GenericBluetoothDeviceListener> genericBluetoothDeviceListeners = new CopyOnWriteArrayList<>();
    private final List<BluetoothSmartDeviceListener> bluetoothSmartDeviceListeners = new CopyOnWriteArrayList<>();

    private boolean authenticated;

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
    private AuthenticationProvider authenticationProvider;

    private final Lock rssiUpdateLock = new ReentrantLock();
    private Filter<Short> rssiFilter = new RssiKalmanFilter();
    private boolean rssiFilteringEnabled = true;
    private long rssiReportingRate = DEFAULT_RSSI_REPORTING_RATE;
    private Instant rssiLastNotified = Instant.now().minusSeconds(60);
    private short measuredTxPower;
    private double signalPropagationExponent;
    private Instant lastAdvertised;
    private short txPower;

    DeviceGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        super(bluetoothManager, url);
    }

    @Override
    void init(Device device) {
        logger.debug("Initializing device governor: {}", url);
        enableRSSINotifications(device);
        enableConnectionNotifications(device);
        enableServicesResolvedNotifications(device);
        enableBlockedNotifications(device);
        enableManufacturerDataNotifications(device);
        enableServiceDataNotifications(device);
        if (device.isConnected()) {
            notifyConnected(true);
            if (device.isServicesResolved()) {
                notifyServicesResolved(getResolvedServices());
                authenticate();
            }
        }
        logger.trace("Device governor initialization performed: {}", url);
    }

    @Override
    void update(Device device) {
        logger.trace("Updating device governor: {}", url);
        AdapterGovernor adapterGovernor = bluetoothManager.getAdapterGovernor(getURL());
        boolean adapterReady = adapterGovernor.isReady();
        boolean adapterPowered = adapterGovernor.isPowered();
        logger.trace("Checking if device adapter is ready / powered: {} : {} / {}",
                url, adapterReady, adapterPowered);
        if (adapterReady && adapterPowered) {
            updateBlocked(device);
            if (!blockedControl) {
                // Note: BlueGiga and TinyB devices work in different way:
                // TinyB would have thrown an exception if the device was out of range (or turned off)
                // BlueGiga would not thrown any exception by now
                // therefore we need to check if BlueGiga device is still alive by querying the device RSSI
                // Further note: TinyB device when connected constantly returns the very last known RSSI
                boolean connected = updateConnected(device);
                if (connected) {
                    logger.debug("Checking if device is still alive by getting its RSSI: {}", url);
                    notifyRSSIChanged(getRSSI());
                    updateLastInteracted();
                } else {
                    // if not connected, there is not any easy way to check if the native object is still
                    // alive simply because multiple adapters can be used and one of them is connected to the device,
                    // hence other corresponding native devices (through other adapters) do not receive
                    // any notifications and also do not receive any advertisements,
                    // in other words they are dead but not exactly
                    // There is a workaround in place to detect such state,
                    // see {@link CombinedDeviceGovernorImpl#update()} method
                }
            }
            txPower = device.getTxPower();
        }

        boolean newOnline = isOnline();

        // if BT device has a private address which changes evey so often, then we have to check if provided native
        // object is stale by looking into new discovered devices and trying to match the old one with new discoveries
        // if the bluetooth device changed its address, then we reset this governor letting it to re-initialize
        if (online && !newOnline && url.getDeviceAddress() == null) {
            AddressType addressType = AddressUtils.guessDeviceAddressType(device.getURL());
            if (addressType == AddressType.RESOLVABLE || addressType == AddressType.NON_RESOLVABLE) {
                BluetoothManagerImpl.DeviceDiscoveryHolder discoveredDevice =
                        bluetoothManager.findDeviceByAttributes(device.getURL().getProtocol(), url);
                if (!discoveredDevice.getURL().equals(device.getURL())) {
                    lastAdvertised = Instant.ofEpochMilli(discoveredDevice.getTimestamp());
                    throw new RuntimeException("Stale object detected");
                }
            }
        }
        logger.trace("Device governor update performed: {}", url);
    }

    @Override
    public void update() {
        super.update();
        updateOnline(isOnline());
    }

    /**
     * This method is called by {@link CombinedDeviceGovernorImpl#update()} to check if all delegates are alive.
     * Note: this is a trade off between bad design and stability.
     * @return true if stale, false otherwise
     */
    boolean checkIfStale() {
        if (isReady() && Instant.now().minusSeconds(RUMP_UP_TIMEOUT).isAfter(getReady())) {
            AdapterGovernor adapterGovernor = bluetoothManager.getAdapterGovernor(getURL());
            if (adapterGovernor.isDiscovering()) {
                int staleTimeout = onlineTimeout * 2;
                boolean stale = lastAdvertised == null || !checkOnline(staleTimeout);
                String lastAdvertisedFmt = Optional.ofNullable(lastAdvertised)
                        .map(advertised -> Math.abs(Duration.between(Instant.now(), advertised).getSeconds()) + "s")
                        .orElse("never");
                String lastInteractedFmt = Optional.ofNullable(getLastInteracted())
                        .map(interacted -> Math.abs(Duration.between(Instant.now(), interacted).getSeconds()) + "s")
                        .orElse("never");
                logger.debug("Device {} last advertised ({}) ago and last interacted ({}) ago. "
                                + "Stale timeout: {}s. Device is considered stale: {}",
                        url, lastAdvertisedFmt, lastInteractedFmt, staleTimeout, stale);
                //TODO there might be a problem when client switches on discovery for adapter, devices will be reset
                return stale;
            }
        }
        return false;
    }

    @Override
    void reset(Device device) {
        logger.debug("Resetting device governor: {}", url);
        try {
            logger.trace("Disable device notifications: {}", url);
            device.disableConnectedNotifications();
            device.disableServicesResolvedNotifications();
            device.disableRSSINotifications();
            device.disableBlockedNotifications();
            device.disableServiceDataNotifications();
            device.disableManufacturerDataNotifications();
            logger.trace("Disconnecting device: {}", url);
            if (device.isConnected()) {
                device.disconnect();
                notifyConnected(false);
            }
        } catch (Exception ex) {
            logger.warn("Error occurred while resetting device: {} : {} ", url, ex.getMessage());
        }
        connectionNotification = null;
        servicesResolvedNotification = null;
        rssiNotification = null;
        blockedNotification = null;
        serviceDataNotification = null;
        manufacturerDataNotification = null;
        setAuthenticated(false);
        logger.trace("Device governor reset performed: {}", url);
    }

    @Override
    public void dispose() {
        super.dispose();
        logger.trace("Disposing device governor: {}", url);
        genericBluetoothDeviceListeners.clear();
        bluetoothSmartDeviceListeners.clear();
        logger.debug("Device governor disposed: {}", url);
    }

    @Override
    public boolean isUpdatable() {
        return bluetoothManager.getAdapterGovernor(url.getAdapterURL()).isReady();
    }

    @Override
    public int getBluetoothClass() throws NotReadyException {
        return interact("getBluetoothClass", Device::getBluetoothClass);
    }

    @Override
    public boolean isBleEnabled() throws NotReadyException {
        return interact("isBleEnabled", Device::isBleEnabled);
    }

    @Override
    public String getName() throws NotReadyException {
        return interact("getName", Device::getName);
    }

    @Override
    public String getAlias() throws NotReadyException {
        return interact("getAlias", Device::getAlias);
    }

    @Override
    public void setAlias(String alias) throws NotReadyException {
        interact("setAlias", Device::setAlias, alias);
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
        logger.debug("Setting connection control: {} : {} / {}", url, this.connectionControl, connectionControl);
        boolean changed = this.connectionControl != connectionControl;
        if (changed) {
            this.connectionControl = connectionControl;
            scheduleUpdate();
        }
    }

    @Override
    public boolean getBlockedControl() {
        return blockedControl;
    }

    @Override
    public void setBlockedControl(boolean blockedControl) {
        logger.debug("Setting blocked control: {} : {}", url, blockedControl);
        this.blockedControl = blockedControl;
    }

    @Override
    public boolean isConnected() throws NotReadyException {
        return interact("isConnected", Device::isConnected);
    }

    @Override
    public boolean isBlocked() throws NotReadyException {
        return interact("isBlocked", Device::isBlocked);
    }

    @Override
    public boolean isOnline() {
        return checkOnline(onlineTimeout);
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
        return interact("getRSSI", Device::getRSSI);
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
    public Instant getLastAdvertised() {
        return lastAdvertised;
    }

    @Override
    public short getTxPower() {
        return txPower;
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
        double estimated = Math.pow(10d,
                ((double) getTxPowerInternal() - rssi) / (10 * getPropagationExponentInternal()));
        logger.trace("Estimated distance: {} : {}", url, estimated);
        return estimated;
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
            return isReady() && interact("isServicesResolved", Device::isServicesResolved);
        } catch (NotReadyException ignore) {
            return false;
        }
    }

    @Override
    public List<GattService> getResolvedServices() throws NotReadyException {
        return interact("getResolvedServices", device -> {
            List<GattService> services = new ArrayList<>();
            for (Service service : device.getServices()) {
                List<GattCharacteristic> characteristics = new ArrayList<>();
                for (Characteristic characteristic : service.getCharacteristics()) {
                    characteristics.add(convert(characteristic));
                }
                services.add(new GattService(service.getURL(), characteristics));
            }
            return services;
        });
    }

    @Override
    public Map<URL, List<CharacteristicGovernor>> getServicesToCharacteristicsMap() throws NotReadyException {
        return interact("getServicesToCharacteristicsMap", device -> {
            Map<URL, List<CharacteristicGovernor>> services = new HashMap<>();
            for (Service service : device.getServices()) {
                URL serviceURL = service.getURL();
                services.put(serviceURL, (List) bluetoothManager.getGovernors(service.getCharacteristics()));
            }
            return services;
        });
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
        return interact("getManufacturerData", Device::getManufacturerData);
    }

    @Override
    public Map<URL, byte[]> getServiceData() {
        return interact("getServiceData", device -> {
            return convert(device.getServiceData());
        });
    }

    @Override
    public void setAuthenticationProvider(AuthenticationProvider authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    @Override
    void notifyLastChanged() {
        notifyLastChanged(BluetoothManagerUtils.max(getLastInteracted(), lastAdvertised));
    }

    void notifyConnected(boolean connected) {
        logger.debug("Notifying device governor listener (connected): {} : {} : {}",
                url, bluetoothSmartDeviceListeners.size(), connected);
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
        logger.debug("Notifying device governor listener (blocked): {} : {} : {}",
                url, genericBluetoothDeviceListeners.size(), blocked);
        BluetoothManagerUtils.forEachSilently(genericBluetoothDeviceListeners,
                listener -> listener.blocked(blocked), logger,"Execution error of a blocked listener");
    }

    void notifyServicesResolved(List<GattService> services) {
        logger.debug("Notifying device governor listener (services resolved): {} : {} : {}",
                url, bluetoothSmartDeviceListeners.size(), services.size());
        BluetoothManagerUtils.forEachSilently(bluetoothSmartDeviceListeners,
                BluetoothSmartDeviceListener::servicesResolved, services, logger,
                "Execution error of a service resolved listener");
    }

    void notifyServicesUnresolved() {
        logger.debug("Notifying device governor listener (services unresolved): {} : {}",
                url, bluetoothSmartDeviceListeners.size());
        BluetoothManagerUtils.forEachSilently(bluetoothSmartDeviceListeners,
                BluetoothSmartDeviceListener::servicesUnresolved, logger,
                "Execution error of a service unresolved listener");
    }

    void updateRSSI(short next) {
        logger.trace("Updating RSSI: {} : {}", url, next);
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
        } else {
            logger.trace("Skipping RSSI update:  {} : {}", url, next);
        }
    }

    void notifyRSSIChanged(short next) {
        if (rssiReportingRate == 0
                || System.currentTimeMillis() - rssiLastNotified.toEpochMilli() > rssiReportingRate) {
            BluetoothManagerUtils.forEachSilently(genericBluetoothDeviceListeners,
                    listener -> listener.rssiChanged(next), logger,
                    "Execution error of a RSSI listener");
            rssiLastNotified = Instant.now();
        }
    }

    void notifyOnline(boolean online) {
        logger.debug("Notifying device governor listener (online): {} : {} : {}",
                url, genericBluetoothDeviceListeners.size(), online);
        BluetoothManagerUtils.forEachSilently(genericBluetoothDeviceListeners,
            listener -> {
                if (online) {
                    listener.online();
                } else {
                    listener.offline();
                }
            }, logger,"Execution error of an online listener");
    }

    void updateLastAdvertised() {
        lastAdvertised = Instant.now();
    }

    private boolean checkOnline(int timeout) {
        long advertised = lastAdvertised != null ? lastAdvertised.toEpochMilli() : 0;
        long interacted = getLastInteracted() != null ? getLastInteracted().toEpochMilli() : 0;
        return Instant.now().minusSeconds(timeout).isBefore(
                Instant.ofEpochMilli(Math.max(advertised, interacted)));
    }

    private List<Characteristic> getAllCharacteristics() throws NotReadyException {
        return interact("getAllCharacteristics", device -> {
            List<Characteristic> characteristics = new ArrayList<>();
            List<Service> services = device.getServices();
            if (services != null) {
                for (Service service : services) {
                    List<Characteristic> chars = service.getCharacteristics();
                    if (chars != null) {
                        characteristics.addAll(chars);
                    }
                }
            }
            return characteristics;
        });
    }

    private void enableConnectionNotifications(Device bluetoothDevice) {
        logger.debug("Enabling connection notification: {} : {}", getURL(), connectionNotification == null);
        if (connectionNotification == null) {
            connectionNotification = new ConnectionNotification();
            bluetoothDevice.enableConnectedNotifications(connectionNotification);
        }
    }

    private void enableBlockedNotifications(Device bluetoothDevice) {
        logger.debug("Enabling blocked notification: {} : {}", getURL(), blockedNotification == null);
        if (blockedNotification == null) {
            blockedNotification = new BlockedNotification();
            bluetoothDevice.enableBlockedNotifications(blockedNotification);
        }
    }

    private void enableServicesResolvedNotifications(Device bluetoothDevice) {
        logger.debug("Enabling services resolved notification: {} : {}",
                getURL(), servicesResolvedNotification == null);
        if (servicesResolvedNotification == null) {
            servicesResolvedNotification = new ServicesResolvedNotification();
            bluetoothDevice.enableServicesResolvedNotifications(servicesResolvedNotification);
        }
    }

    private void enableRSSINotifications(Device bluetoothDevice) {
        logger.debug("Enabling RSSI notification: {} : {}", getURL(), rssiNotification == null);
        if (rssiNotification == null) {
            rssiNotification = new RSSINotification();
            bluetoothDevice.enableRSSINotifications(rssiNotification);
        }
    }

    private void enableServiceDataNotifications(Device bluetoothDevice) {
        logger.debug("Enabling service data notification: {} : {}", getURL(), serviceDataNotification == null);
        if (serviceDataNotification == null) {
            serviceDataNotification = new ServiceDataNotification();
            bluetoothDevice.enableServiceDataNotifications(serviceDataNotification);
        }
    }

    private void enableManufacturerDataNotifications(Device bluetoothDevice) {
        logger.debug("Enabling manufacturer data notification: {} : {}",
                getURL(), manufacturerDataNotification == null);
        if (manufacturerDataNotification == null) {
            manufacturerDataNotification = new ManufacturerDataNotification();
            bluetoothDevice.enableManufacturerDataNotifications(manufacturerDataNotification);
        }
    }

    private void updateCharacteristics() {
        logger.debug("Updating device governor characteristics: {}", url);
        bluetoothManager.updateDescendants(url);
    }

    private void resetCharacteristics() {
        logger.debug("Resetting device governor characteristics: {}", url);
        bluetoothManager.resetDescendants(url);
    }

    private static GattCharacteristic convert(Characteristic characteristic) {
        return new GattCharacteristic(characteristic.getURL(), characteristic.getFlags());
    }

    private void updateOnline(boolean online) {
        logger.trace("Updating device governor online state: {}", url);
        if (online != this.online) {
            logger.debug("Updating online state: {} : {} (current) / {} (new)", url, this.online, online);
            notifyOnline(online);
        }
        this.online = online;
    }

    private void updateBlocked(Device device) {
        logger.trace("Updating device governor blocked state: {}", url);
        boolean blocked = device.isBlocked();
        if (blockedControl != blocked) {
            logger.debug("Updating blocked state: {} : {} (control) / {} (state)", url, blockedControl, blocked);
            device.setBlocked(blockedControl);
        }
    }

    private boolean updateConnected(Device device) {
        logger.trace("Updating device governor connected state: {}", url);
        boolean connected = isConnected();
        logger.trace("Connected state: {} : {} (control) / {} (state)", url, connectionControl, connected);
        if (connectionControl && !connected && isOnline()) {
            logger.debug("Connecting device: {}", url);
            // if connect returns true, it does not mean that it is already connected, it means that the connection
            // procedure has been started successfully, a connection event should indicate when the procedure finishes
            if (!device.connect()) {
                throw new NotReadyException("Could not connect to device: " + url);
            }
            connected = true;
        } else if (!connectionControl && connected) {
            logger.debug("Disconnecting device: {}", url);
            if (!device.disconnect()) {
                throw new NotReadyException("Could not disconnect from device: " + url);
            }
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

    private Map<URL, byte[]> convert(Map<String, byte[]> serviceData) {
        return serviceData.entrySet().stream()
                .collect(Collectors.toMap(entry -> url.copyWithService(entry.getKey()), Map.Entry::getValue)) ;
    }

    private class ConnectionNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean connected) {
            logger.debug("Connected (notification): {} : {}", url, connected);
            notifyConnected(connected);
            if (!connected) {
                resetCharacteristics();
                setAuthenticated(false);
            }
            updateLastInteracted();
        }
    }

    private class BlockedNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean blocked) {
            logger.debug("Blocked (notification): {} : {}", url, blocked);
            notifyBlocked(blocked);
            updateLastInteracted();
        }
    }

    private class ServicesResolvedNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean serviceResolved) {
            logger.debug("Services resolved (notification): {} : {}", url, serviceResolved);

            if (serviceResolved) {
                List<GattService> gattServices = getResolvedServices();
                if (gattServices != null) {
                    notifyServicesResolved(gattServices);
                }
                authenticate();
                updateCharacteristics();
                updateLastInteracted();
            } else {
                logger.debug("Resetting characteristic governors due to services unresolved event: {}", url);
                setAuthenticated(false);
                resetCharacteristics();
                notifyServicesUnresolved();
            }
        }
    }

    private void authenticate() {
        logger.debug("Performing authentication with the device: {}", url);
        try {
            if (authenticationProvider != null) {
                authenticationProvider.authenticate(bluetoothManager, this);
            }
            setAuthenticated(true);
            BluetoothManagerUtils.forEachSilently(bluetoothSmartDeviceListeners,
                    BluetoothSmartDeviceListener::authenticated, logger,
                    "Execution error of a authenticated listener");
        } catch (BluetoothAuthenticationException e) {
            logger.warn("Authentication failure: {} : {}", url, e.getMessage());
            setAuthenticated(false);
            BluetoothManagerUtils.forEachSilently(bluetoothSmartDeviceListeners,
                    listener -> listener.authenticationFailure(e), logger,
                    "Execution error of a authentication failed listener");
        }
    }

    private class RSSINotification implements Notification<Short> {
        @Override
        public void notify(Short rssi) {
            updateRSSI(rssi);
            updateLastAdvertised();
            updateOnline(true);
        }
    }

    private class ServiceDataNotification implements Notification<Map<String, byte[]>> {
        @Override
        public void notify(Map<String, byte[]> serviceData) {
            logger.trace("Services data changed (notification): {} : {} : {}",
                    url, bluetoothSmartDeviceListeners.size(), serviceData.size());
            BluetoothManagerUtils.forEachSilently(bluetoothSmartDeviceListeners,
                listener -> listener.serviceDataChanged(convert(serviceData)), logger,
                    "Execution error of a service data listener");
            updateLastAdvertised();
        }
    }

    private class ManufacturerDataNotification implements Notification<Map<Short, byte[]>> {
        @Override
        public void notify(Map<Short, byte[]> manufacturerData) {
            logger.debug("Manufacturer data changed (notification): {} : {} : {}",
                    url, bluetoothSmartDeviceListeners.size(), manufacturerData.size());
            BluetoothManagerUtils.forEachSilently(bluetoothSmartDeviceListeners,
                listener -> listener.manufacturerDataChanged(manufacturerData), logger,
                    "Execution error of a manufacturer data listener");
            updateLastAdvertised();
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
