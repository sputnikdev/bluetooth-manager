package org.sputnikdev.bluetooth.manager.impl.tinyb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.gattparser.URL;
import org.sputnikdev.bluetooth.manager.BluetoothSmartDeviceListener;
import org.sputnikdev.bluetooth.manager.CharacteristicListener;
import org.sputnikdev.bluetooth.manager.GattCharacteristic;
import org.sputnikdev.bluetooth.manager.GattService;
import org.sputnikdev.bluetooth.manager.GenericBluetoothDeviceListener;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothDevice;
import tinyb.BluetoothException;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothGattService;
import tinyb.BluetoothManager;
import tinyb.BluetoothNotification;
import tinyb.BluetoothType;

public class DeviceGovernor extends BluetoothObjectGovernor<BluetoothDevice> {

    private Logger logger = LoggerFactory.getLogger(DeviceGovernor.class);

    private GenericBluetoothDeviceListener genericBluetoothDeviceListener;
    private final List<BluetoothSmartDeviceListener> bluetoothSmartDeviceListeners = new ArrayList<>();
    private ConnectionNotification connectionNotification;
    private BlockedNotification blockedNotification;
    private ServicesResolvedNotification servicesResolvedNotification;
    private RSSINotification rssiNotification;
    private final Map<URL, CharacteristicGovernor> characteristicGovernors = new HashMap<>();
    private boolean connectionEnabled;
    private boolean blockedControl;
    private Date lastActivity = new Date();
    private boolean online = false;

    DeviceGovernor(URL url) {
        super(url);
    }

    @Override
    void init(BluetoothDevice device) {
        enableRSSINotifications(device);
        enableConnectionNotifications(device);
        enableServicesResolvedNotifications(device);
        enableBlockedNotifications(device);
    }

    @Override
    void updateState(BluetoothDevice device) {
        boolean blocked = device.getBlocked();

        if (blockedControl != blocked) {
            device.setBlocked(blockedControl);
            blocked = blockedControl;
        }

        if (!blocked) {
            boolean connected = device.getConnected();
            if (connectionEnabled && !connected) {
                connected = device.connect();
            } else if (!connectionEnabled && connected) {
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
    void disableNotifications(BluetoothDevice device) {
        logger.info("Disable device notifications: " + getURL());
        device.disableConnectedNotifications();
        device.disableServicesResolvedNotifications();
        device.disableRSSINotifications();
        device.disableBlockedNotifications();
        connectionNotification = null;
        servicesResolvedNotification = null;
    }

    @Override
    BluetoothDevice findBluetoothObject() {
        BluetoothAdapter adapter = (BluetoothAdapter) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.ADAPTER, null, getURL().getAdapterAddress(), null);
        return (BluetoothDevice) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.DEVICE, null, getURL().getDeviceAddress(), adapter);
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
            BluetoothDevice device = getBluetoothObject();
            if (device != null && device.getConnected()) {
                logger.info("Disconnecting device: " + getURL());
                device.disconnect();
                notifyConnected(false);
            }
        } catch (BluetoothException ex) {
            logger.warn("Could not disconnect device: " + getURL(), ex);
        }
        reset();
        synchronized (this.bluetoothSmartDeviceListeners) {
            this.bluetoothSmartDeviceListeners.clear();
        }
        this.genericBluetoothDeviceListener = null;
        logger.info("Device governor has been disposed: " + getURL());
    }

    boolean isConnectionEnabled() {
        return connectionEnabled;
    }

    void setConnectionEnabled(boolean connectionEnabled) {
        this.connectionEnabled = connectionEnabled;
    }

    boolean getBlockedControl() {
        return this.blockedControl;
    }

    void setBlockedControl(boolean blockedControl) {
        this.blockedControl = blockedControl;
    }

    boolean isConnected() {
        try {
            BluetoothDevice device = getBluetoothObject();
            return device != null && device.getConnected();
        } catch (BluetoothException ex) {
            logger.error("Could not get connection status", ex);
            return false;
        }
    }

    boolean isBlocked() {
        try {
            BluetoothDevice device = getBluetoothObject();
            return device != null && device.getBlocked();
        } catch (BluetoothException ex) {
            logger.error("Could not get blocked status", ex);
            return false;
        }
    }

    boolean isOnline() {
        return Instant.now().minusSeconds(20).isBefore(this.lastActivity.toInstant());
    }

    short getRSSI() {
        try {
            BluetoothDevice device = getBluetoothObject();
            return device != null ? device.getRSSI() : 0;
        } catch (BluetoothException ex) {
            logger.error("Could not get RSSI status", ex);
            return 0;
        }
    }

    void addCharacteristicListener(URL url, CharacteristicListener characteristicListener) {
        getCharacteristicGovernor(url).setCharacteristicListener(characteristicListener);
    }

    void removeCharacteristicListener(URL url) {
        URL characteristicURL = url.getCharacteristicURL();
        synchronized (characteristicGovernors) {
            if (characteristicGovernors.containsKey(characteristicURL)) {
                getCharacteristicGovernor(url).setCharacteristicListener(null);
            }
        }
    }

    byte[] read(URL url) {
        return getCharacteristicGovernor(url).read();
    }

    boolean write(URL url, byte[] data) {
        return getCharacteristicGovernor(url).write(data);
    }

    private CharacteristicGovernor getCharacteristicGovernor(URL url) {
        URL characteristicURL = url.getCharacteristicURL();
        synchronized (characteristicGovernors) {
            if (!characteristicGovernors.containsKey(characteristicURL)) {
                CharacteristicGovernor governor = new CharacteristicGovernor(this, characteristicURL);
                try {
                    governor.update();
                } catch (Exception ex) {
                    logger.error("Could not update characteristic governor: " + governor.getURL(), ex);
                }
                characteristicGovernors.put(characteristicURL, governor);
                return governor;
            }
            return characteristicGovernors.get(characteristicURL);
        }
    }

    void addBluetoothSmartDeviceListener(BluetoothSmartDeviceListener bluetoothSmartDeviceListener) {
        synchronized (this.bluetoothSmartDeviceListeners) {
            this.bluetoothSmartDeviceListeners.add(bluetoothSmartDeviceListener);
        }
    }

    void removeBluetoothSmartDeviceListener(BluetoothSmartDeviceListener bluetoothSmartDeviceListener) {
        synchronized (this.bluetoothSmartDeviceListeners) {
            this.bluetoothSmartDeviceListeners.remove(bluetoothSmartDeviceListener);
        }
    }

    void setGenericBluetoothDeviceListener(GenericBluetoothDeviceListener genericBluetoothDeviceListener) {
        this.genericBluetoothDeviceListener = genericBluetoothDeviceListener;
    }

    void updateLastUpdated() {
        this.lastActivity = new Date();
        notifyLastActivityChanged(this.lastActivity);
    }

    private void enableConnectionNotifications(BluetoothDevice bluetoothDevice) {
        if (this.connectionNotification == null) {
            logger.info("Enabling connection notification: {} ", getURL());
            this.connectionNotification = new ConnectionNotification();
            bluetoothDevice.enableConnectedNotifications(connectionNotification);
        }
    }

    private void enableBlockedNotifications(BluetoothDevice bluetoothDevice) {
        if (this.blockedNotification == null) {
            logger.info("Enabling blocked notification: {} ", getURL());
            this.blockedNotification = new BlockedNotification();
            bluetoothDevice.enableBlockedNotifications(blockedNotification);
        }
    }

    private void enableServicesResolvedNotifications(BluetoothDevice bluetoothDevice) {
        if (this.servicesResolvedNotification == null) {
            logger.info("Enabling services resolved notification: {} ", getURL());
            this.servicesResolvedNotification = new ServicesResolvedNotification();
            bluetoothDevice.enableServicesResolvedNotifications(this.servicesResolvedNotification);
        }
    }

    private void enableRSSINotifications(BluetoothDevice bluetoothDevice) {
        if (rssiNotification == null) {
            logger.info("Enabling RSSI notification: {} ", getURL());
            this.rssiNotification = new RSSINotification();
            bluetoothDevice.enableRSSINotifications(rssiNotification);
        }
    }

    private List<GattService> servicesResolved() {
        logger.info("Services resolved: " + getURL());
        List<GattService> services = new ArrayList<>();
        for (BluetoothGattService service : getBluetoothObject().getServices()) {
            List<GattCharacteristic> characteristics = new ArrayList<>();
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                characteristics.add(convert(characteristic));
            }
            services.add(new GattService(service.getUUID(), characteristics));
        }
        return services;

    }

    private void updateCharacteristics() {
        synchronized (characteristicGovernors) {
            for (CharacteristicGovernor governor : characteristicGovernors.values()) {
                try {
                    governor.update();
                } catch (Throwable ex) {
                    logger.error("Could not update characteristic governor", ex);
                }
            }
        }
    }

    private void resetCharacteristics() {
        synchronized (characteristicGovernors) {
            for (CharacteristicGovernor governor : characteristicGovernors.values()) {
                try {
                    governor.reset();
                } catch (Throwable ex) {
                    logger.warn("Could not reset characteristic governor", ex);
                }
            }
        }
    }

    private GattCharacteristic convert(BluetoothGattCharacteristic characteristic) {
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
        try {
            GenericBluetoothDeviceListener listener = this.genericBluetoothDeviceListener;
            if (listener != null) {
                listener.blocked(blocked);
            }
        } catch (Exception ex) {
            logger.error("Execution error of a Blocked listener", ex);
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
        try {
            GenericBluetoothDeviceListener listener = this.genericBluetoothDeviceListener;
            if (listener != null) {
                listener.rssiChanged(rssi != null ? rssi : 0);
            }
        } catch (Exception ex) {
            logger.error("Execution error of a RSSI listener", ex);
        }
    }

    private void notifyLastActivityChanged(Date date) {
        try {
            GenericBluetoothDeviceListener listener = this.genericBluetoothDeviceListener;
            if (listener != null) {
                listener.lastUpdatedChanged(date);
            }
        } catch (Exception ex) {
            logger.error("Execution error of a last activity listener", ex);
        }
    }

    private void updateOnline(boolean online) {
        if (online != this.online) {
            notifyOnline(online);
        }
        this.online = online;
    }

    private void notifyOnline(boolean online) {
        if (this.online == online) {
            return;
        }
        try {
            GenericBluetoothDeviceListener listener = this.genericBluetoothDeviceListener;
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

    private class ConnectionNotification implements BluetoothNotification<Boolean> {
        @Override
        public void run(Boolean connected) {
            logger.info("Connected (notification): " + getURL() + " " + connected);
            notifyConnected(connected);
            updateLastUpdated();
        }
    }

    private class BlockedNotification implements BluetoothNotification<Boolean> {
        @Override
        public void run(Boolean blocked) {
            logger.info("Blocked (notification): " + getURL() + " " + blocked);
            notifyBlocked(blocked);
            updateLastUpdated();
        }
    }

    private class ServicesResolvedNotification implements BluetoothNotification<Boolean> {
        @Override
        public void run(Boolean serviceResolved) {
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

    private class RSSINotification implements BluetoothNotification<Short> {
        @Override
        public void run(Short rssi) {
            notifyRSSIChanged(rssi);
            updateLastUpdated();
        }
    }

}
