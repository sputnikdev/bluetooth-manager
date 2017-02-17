package org.sputnikdev.bluetooth.manager.impl;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.gattparser.URL;
import org.sputnikdev.bluetooth.manager.AdapterListener;

class AdapterGovernor extends BluetoothObjectGovernor<Adapter> {

    private Logger logger = LoggerFactory.getLogger(AdapterGovernor.class);

    private AdapterListener adapterListener;

    private PoweredNotification poweredNotification;
    private DiscoveringNotification discoveringNotification;

    private Date lastActivity = new Date();
    private String alias;
    private boolean poweredControl = true;
    private boolean discoveringControl = true;

    AdapterGovernor(URL url) {
        super(url);
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
    }

    void dispose() {
        logger.info("Disposing adapter governor: " + getURL());
        reset();
        this.adapterListener = null;
        logger.info("Adapter governor has been disposed: " + getURL());
    }

    boolean getPoweredControl() {
        return poweredControl;
    }

    void setPoweredControl(boolean poweredControl) {
        this.poweredControl = poweredControl;
    }

    boolean isPowered() {
        Adapter adapter = getBluetoothObject();
        return adapter != null && adapter.isPowered();
    }

    boolean getDiscoveringControl() {
        return discoveringControl;
    }

    void setDiscoveringControl(boolean discovering) {
        this.discoveringControl = discovering;
    }

    boolean isDiscovering() {
        Adapter adapter = getBluetoothObject();
        return adapter != null && adapter.isDiscovering();
    }

    void setAlias(String alias) {
        this.alias = alias;
    }

    String getAlias() {
        return this.alias;
    }

    void setAdapterListener(AdapterListener adapterListener) {
        this.adapterListener = adapterListener;
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

    private void updateLastUpdated() {
        this.lastActivity = new Date();
        notifyLastActivityChanged(this.lastActivity);
    }

    private void enablePoweredNotifications(Adapter adapter) {
        if (this.poweredNotification == null && adapterListener != null) {
            this.poweredNotification = new PoweredNotification();
            adapter.enablePoweredNotifications(this.poweredNotification);
            notifyPowered(adapter.isPowered());
        }
    }

    private void enableDiscoveringNotifications(Adapter adapter) {
        if (this.discoveringNotification == null && adapterListener != null) {
            this.discoveringNotification = new DiscoveringNotification();
            adapter.enableDiscoveringNotifications(this.discoveringNotification);
            notifyDiscovering(adapter.isDiscovering());
        }
    }

    private void notifyPowered(boolean powered) {
        try {
            AdapterListener listener = this.adapterListener;
            if (listener != null) {
                listener.powered(powered);
            }
        } catch (Exception ex) {
            logger.error("Execution error of a powered listener", ex);
        }
    }

    private void notifyDiscovering(boolean discovering) {
        try {
            AdapterListener listener = this.adapterListener;
            if (listener != null) {
                listener.discovering(discovering);
            }
        } catch (Exception ex) {
            logger.error("Execution error of a powered listener", ex);
        }
    }

    private void notifyLastActivityChanged(Date date) {
        try {
            AdapterListener listener = this.adapterListener;
            if (listener != null) {
                listener.lastUpdatedChanged(date);
            }
        } catch (Exception ex) {
            logger.error("Execution error of a last activity listener: " + getURL(), ex);
        }
    }

    private class PoweredNotification implements Notification<Boolean> {
        @Override
        public void notify(Boolean powered) {
            notifyPowered(powered);
            updateLastUpdated();
            if (!powered && AdapterGovernor.this.findBluetoothObject() == null) {
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
