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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.AdapterGovernor;
import org.sputnikdev.bluetooth.manager.AdapterListener;
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.NotReadyException;

/**
 *
 * @author Vlad Kolotov
 */
class AdapterGovernorImpl extends BluetoothObjectGovernor<Adapter> implements AdapterGovernor {

    private Logger logger = LoggerFactory.getLogger(AdapterGovernorImpl.class);

    private final List<AdapterListener> adapterListeners = new ArrayList<>();

    private PoweredNotification poweredNotification;
    private DiscoveringNotification discoveringNotification;

    private String alias;
    private boolean poweredControl = true;
    private boolean discoveringControl = true;

    AdapterGovernorImpl(BluetoothManagerImpl bluetoothManager, URL url) {
        super(bluetoothManager, url);
    }

    void init(Adapter adapter) {
        notifyPowered(adapter.isPowered());
        enablePoweredNotifications(adapter);
        enableDiscoveringNotifications(adapter);
        updateLastUpdated();
    }

    void updateState(Adapter adapter) {
        updatePowered(adapter);
        updateAlias(adapter);
        updateDiscovering(adapter);
    }

    @Override
    Adapter findBluetoothObject() {
        return BluetoothObjectFactory.getDefault().getAdapter(getURL());
    }

    @Override
    void disableNotifications(Adapter adapter) {
        adapter.disablePoweredNotifications();
        adapter.disableDiscoveringNotifications();
        poweredNotification = null;
        discoveringNotification = null;
    }

    void dispose() {
        logger.info("Disposing adapter governor: " + getURL());
        synchronized (this.adapterListeners) {
            this.adapterListeners.clear();
        }
        super.dispose();
        logger.info("Adapter governor has been disposed: " + getURL());
    }

    @Override
    public boolean getPoweredControl() {
        return poweredControl;
    }

    @Override
    public void setPoweredControl(boolean poweredControl) {
        this.poweredControl = poweredControl;
    }

    @Override
    public boolean isPowered() throws NotReadyException {
        Adapter adapter = getBluetoothObject();
        return adapter != null && adapter.isPowered();
    }

    @Override
    public boolean getDiscoveringControl() {
        return discoveringControl;
    }

    @Override
    public void setDiscoveringControl(boolean discovering) {
        this.discoveringControl = discovering;
    }

    @Override
    public boolean isDiscovering() throws NotReadyException {
        Adapter adapter = getBluetoothObject();
        return adapter != null && adapter.isDiscovering();
    }

    @Override
    public void setAliasControl(String alias) {
        this.alias = alias;
    }

    @Override
    public String getAliasControl() {
        return this.alias;
    }

    @Override
    public String getAlias() throws NotReadyException {
        return getBluetoothObject().getAlias();
    }

    @Override
    public String getName() throws NotReadyException {
        return getBluetoothObject().getName();
    }

    @Override
    public String getDisplayName() throws NotReadyException {
        return this.alias != null ? this.alias : getName();
    }

    @Override
    public List<URL> getDevices() throws NotReadyException {
        return BluetoothManagerUtils.getURLs(getBluetoothObject().getDevices());
    }

    @Override
    public List<DeviceGovernor> getDeviceGovernors() throws NotReadyException {
        return (List) bluetoothManager.getGovernors(getBluetoothObject().getDevices());
    }

    @Override
    public String toString() {
        String result = "[Adapter] " + getURL();
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
        return BluetoothObjectType.ADAPTER;
    }

    @Override
    public void accept(BluetoothObjectVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    @Override
    public void addAdapterListener(AdapterListener adapterListener) {
        synchronized (this.adapterListeners) {
            this.adapterListeners.add(adapterListener);
        }
    }

    @Override
    public void removeAdapterListener(AdapterListener adapterListener) {
        synchronized (this.adapterListeners) {
            this.adapterListeners.remove(adapterListener);
        }
    }

    private void updateAlias(Adapter adapter) {
        if (this.alias == null) {
            this.alias = adapter.getAlias();
        } else if (!this.alias.equals(adapter.getAlias())) {
            adapter.setAlias(this.alias);
        }
    }

    private void updatePowered(Adapter adapter) {
        if (this.poweredControl != adapter.isPowered()) {
            adapter.setPowered(this.poweredControl);
        }
    }

    private void updateDiscovering(Adapter adapter) {
        if (adapter.isPowered()) {
            if (discoveringControl && !adapter.isDiscovering()) {
                adapter.startDiscovery();
            } else if (!discoveringControl && adapter.isDiscovering()) {
                adapter.stopDiscovery();
            }
        }
    }

    private void enablePoweredNotifications(Adapter adapter) {
        if (this.poweredNotification == null) {
            this.poweredNotification = new PoweredNotification();
            adapter.enablePoweredNotifications(this.poweredNotification);
            notifyPowered(adapter.isPowered());
        }
    }

    private void enableDiscoveringNotifications(Adapter adapter) {
        if (this.discoveringNotification == null) {
            this.discoveringNotification = new DiscoveringNotification();
            adapter.enableDiscoveringNotifications(this.discoveringNotification);
            notifyDiscovering(adapter.isDiscovering());
        }
    }

    private void notifyPowered(boolean powered) {
        synchronized (this.adapterListeners) {
            for (AdapterListener listener : this.adapterListeners) {
                try {
                    listener.powered(powered);
                } catch (Exception ex) {
                    logger.error("Execution error of a powered listener: " + powered, ex);
                }
            }
        }
    }

    private void notifyDiscovering(boolean discovering) {
        synchronized (this.adapterListeners) {
            for (AdapterListener listener : this.adapterListeners) {
                try {
                    listener.powered(discovering);
                } catch (Exception ex) {
                    logger.error("Execution error of a discovering listener: " + discovering, ex);
                }
            }
        }
    }

    private class PoweredNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean powered) {
            notifyPowered(powered);
            updateLastUpdated();
            if (!powered && AdapterGovernorImpl.this.findBluetoothObject() == null) {
                reset();
            }
        }
    }

    private class DiscoveringNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean discovering) {
            notifyDiscovering(discovering);
            updateLastUpdated();
        }
    }
}
