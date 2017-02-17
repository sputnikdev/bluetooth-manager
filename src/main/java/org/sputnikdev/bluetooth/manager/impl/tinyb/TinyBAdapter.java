package org.sputnikdev.bluetooth.manager.impl.tinyb;

import org.sputnikdev.bluetooth.manager.impl.Adapter;
import org.sputnikdev.bluetooth.manager.impl.Notification;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothException;
import tinyb.BluetoothNotification;

public class TinyBAdapter implements Adapter<BluetoothAdapter> {

    private final BluetoothAdapter adapter;

    public TinyBAdapter(BluetoothAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public String getAlias() {
        return adapter.getAlias();
    }

    @Override
    public String getName() {
        return adapter.getName();
    }

    @Override
    public void setAlias(String s) {
        adapter.setAlias(s);
    }

    @Override
    public String getAddress() {
        return adapter.getAddress();
    }

    @Override
    public boolean isPowered() {
        return adapter.getPowered();
    }

    @Override
    public void enablePoweredNotifications(Notification<Boolean> notification) {
        adapter.enablePoweredNotifications(new BluetoothNotification<Boolean>() {
            @Override public void run(Boolean powered) {
                notification.notify(powered);
            }
        });
    }

    @Override
    public void disablePoweredNotifications() {
        adapter.disablePoweredNotifications();
    }

    @Override
    public void setPowered(boolean b) {
        adapter.setPowered(b);
    }

    @Override
    public boolean isDiscovering() {
        return adapter.getDiscoverable();
    }

    @Override
    public void enableDiscoveringNotifications(Notification<Boolean> notification) {
        adapter.enableDiscoveringNotifications(new BluetoothNotification<Boolean>() {
            @Override public void run(Boolean value) {
                notification.notify(value);
            }
        });
    }

    @Override
    public void disableDiscoveringNotifications() {
        adapter.disableDiscoveringNotifications();
    }

    @Override
    public boolean startDiscovery() throws BluetoothException {
        return adapter.startDiscovery();
    }

    @Override
    public boolean stopDiscovery() throws BluetoothException {
        return adapter.stopDiscovery();
    }
}
