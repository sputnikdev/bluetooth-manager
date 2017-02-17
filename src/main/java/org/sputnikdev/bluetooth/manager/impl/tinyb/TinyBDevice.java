package org.sputnikdev.bluetooth.manager.impl.tinyb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sputnikdev.bluetooth.manager.impl.Device;
import org.sputnikdev.bluetooth.manager.impl.Notification;
import org.sputnikdev.bluetooth.manager.impl.Service;
import tinyb.BluetoothDevice;
import tinyb.BluetoothException;
import tinyb.BluetoothGattService;
import tinyb.BluetoothNotification;

public class TinyBDevice implements Device<BluetoothDevice> {

    private final BluetoothDevice device;

    public TinyBDevice(BluetoothDevice device) {
        this.device = device;
    }

    @Override
    public boolean disconnect() throws BluetoothException {
        return device.disconnect();
    }

    @Override
    public boolean connect() throws BluetoothException {
        return device.connect();
    }

    @Override
    public String getAddress() {
        return device.getAddress();
    }

    @Override
    public String getName() {
        return device.getName();
    }

    @Override
    public String getAlias() {
        return device.getAlias();
    }

    @Override
    public void setAlias(String alias) {
        device.setAlias(alias);
    }

    @Override
    public boolean isBlocked() {
        return device.getBlocked();
    }

    @Override
    public void enableBlockedNotifications(Notification<Boolean> notification) {
        device.enableBlockedNotifications(new BluetoothNotification<Boolean>() {
            @Override public void run(Boolean value) {
                notification.notify(value);
            }
        });
    }

    @Override
    public void disableBlockedNotifications() {
        device.disableBlockedNotifications();
    }

    @Override
    public void setBlocked(boolean blocked) {
        device.setBlocked(blocked);
    }

    @Override
    public short getRSSI() {
        return device.getRSSI();
    }

    @Override
    public void enableRSSINotifications(Notification<Short> notification) {
        device.enableRSSINotifications(new BluetoothNotification<Short>() {
            @Override public void run(Short value) {
                notification.notify(value);
            }
        });
    }

    @Override
    public void disableRSSINotifications() {
        device.disableRSSINotifications();
    }

    @Override
    public boolean isConnected() {
        return device.getConnected();
    }

    @Override
    public void enableConnectedNotifications(Notification<Boolean> notification) {
        device.enableConnectedNotifications(new BluetoothNotification<Boolean>() {
            @Override public void run(Boolean value) {
                notification.notify(value);
            }
        });
    }

    @Override
    public void disableConnectedNotifications() {
        device.disableConnectedNotifications();
    }

    @Override
    public boolean isServicesResolved() {
        return device.getServicesResolved();
    }

    @Override
    public void enableServicesResolvedNotifications(Notification<Boolean> notification) {
        device.enableServicesResolvedNotifications(new BluetoothNotification<Boolean>() {
            @Override public void run(Boolean value) {
                notification.notify(value);
            }
        });
    }

    @Override
    public void disableServicesResolvedNotifications() {
        device.disableServicesResolvedNotifications();
    }

    @Override
    public List<Service> getServices() {
        List<BluetoothGattService> services = device.getServices();
        List<Service> result = new ArrayList<>(services.size());
        for (BluetoothGattService nativeService : services) {
            result.add(new TinyBService(nativeService));
        }
        return Collections.unmodifiableList(result);
    }
}
