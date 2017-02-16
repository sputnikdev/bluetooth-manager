package org.sputnikdev.bluetooth.manager.impl.tinyb;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.gattparser.URL;
import org.sputnikdev.bluetooth.manager.AdapterListener;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothManager;
import tinyb.BluetoothNotification;
import tinyb.BluetoothType;

public class AdapterGovernor extends BluetoothObjectGovernor<BluetoothAdapter> {

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

    void init(BluetoothAdapter adapter) {
        notifyPowered(adapter.getPowered());
        enablePoweredNotifications(adapter);
        enableDiscoveringNotifications(adapter);
        updateLastUpdated();
    }

    void updateState(BluetoothAdapter adapter) {
        updatePowered(adapter);
        updateAlias(adapter);
        updateDiscovering(adapter);
    }

    @Override
    BluetoothAdapter findBluetoothObject() {
        return (BluetoothAdapter) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.ADAPTER, null, getURL().getAdapterAddress(), null);
    }

    @Override
    void disableNotifications(BluetoothAdapter adapter) {
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
        BluetoothAdapter adapter = getBluetoothObject();
        return adapter != null && adapter.getPowered();
    }

    boolean getDiscoveringControl() {
        return discoveringControl;
    }

    void setDiscoveringControl(boolean discovering) {
        this.discoveringControl = discovering;
    }

    boolean isDiscovering() {
        BluetoothAdapter adapter = getBluetoothObject();
        return adapter != null && adapter.getDiscovering();
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

    private void updateAlias(BluetoothAdapter adapter) {
        if (this.alias == null) {
            this.alias = adapter.getAlias();
        } else if (!this.alias.equals(adapter.getAlias())) {
            adapter.setAlias(this.alias);
        }
    }

    private void updatePowered(BluetoothAdapter adapter) {
        if (this.poweredControl != adapter.getPowered()) {
            adapter.setPowered(this.poweredControl);
        }
    }

    private void updateDiscovering(BluetoothAdapter adapter) {
        if (adapter.getPowered()) {
            if (discoveringControl && !adapter.getDiscovering()) {
                adapter.startDiscovery();
            } else if (!discoveringControl && adapter.getDiscovering()) {
                adapter.stopDiscovery();
            }
        }
    }

    private void updateLastUpdated() {
        this.lastActivity = new Date();
        notifyLastActivityChanged(this.lastActivity);
    }

    private void enablePoweredNotifications(BluetoothAdapter adapter) {
        if (this.poweredNotification == null && adapterListener != null) {
            this.poweredNotification = new PoweredNotification();
            adapter.enablePoweredNotifications(this.poweredNotification);
            notifyPowered(adapter.getPowered());
        }
    }

    private void enableDiscoveringNotifications(BluetoothAdapter adapter) {
        if (this.discoveringNotification == null && adapterListener != null) {
            this.discoveringNotification = new DiscoveringNotification();
            adapter.enableDiscoveringNotifications(this.discoveringNotification);
            notifyDiscovering(adapter.getDiscovering());
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

    private class PoweredNotification implements BluetoothNotification<Boolean> {
        @Override
        public void run(Boolean powered) {
            notifyPowered(powered);
            updateLastUpdated();
            if (!powered && AdapterGovernor.this.findBluetoothObject() == null) {
                reset();
            }
        }
    }

    private class DiscoveringNotification implements BluetoothNotification<Boolean> {
        @Override
        public void run(Boolean discovering) {
            notifyDiscovering(discovering);
            updateLastUpdated();
        }
    }
}
