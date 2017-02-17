package org.sputnikdev.bluetooth.manager.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.gattparser.URL;
import org.sputnikdev.bluetooth.manager.AdapterListener;
import org.sputnikdev.bluetooth.manager.BluetoothSmartDeviceListener;
import org.sputnikdev.bluetooth.manager.BluetoothManager;
import org.sputnikdev.bluetooth.manager.CharacteristicListener;
import org.sputnikdev.bluetooth.manager.DeviceDiscoveryListener;
import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
import org.sputnikdev.bluetooth.manager.GenericBluetoothDeviceListener;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothDevice;
import tinyb.BluetoothException;

class BluetoothManagerImpl implements BluetoothManager {

    private Logger logger = LoggerFactory.getLogger(BluetoothManagerImpl.class);

    private final ScheduledExecutorService singleThreadScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final Set<DeviceDiscoveryListener> deviceDiscoveryListeners = new HashSet<>();

    private final Map<URL, BluetoothObjectGovernor> governors = new HashMap<>();

    private ScheduledFuture discoveryFuture;
    private final Map<URL, ScheduledFuture> governorFutures = new HashMap<>();

    @Override
    public synchronized void startDiscovery() {
        try {
            if (discoveryFuture == null) {
                discoveryFuture = singleThreadScheduler.scheduleAtFixedRate(
                        new DiscoveryJob(), 0, 10, TimeUnit.SECONDS);
            }
        } catch (BluetoothException ex) {
            logger.error("Could not start discovery", ex);
        }
    }

    @Override
    public synchronized void stopDiscovery() {
        try {
            if (discoveryFuture != null) {
                tinyb.BluetoothManager.getBluetoothManager().stopDiscovery();
                discoveryFuture.cancel(true);
            }
        } catch (BluetoothException ex) {
            logger.error("Could not stop discovery", ex);
        }
    }

    @Override
    public void addAdapterListener(URL url, AdapterListener adapterListener) {
        getAdapterGovernor(url).setAdapterListener(adapterListener);
    }

    @Override
    public void removeAdapterListener(URL url) {
        getAdapterGovernor(url).setAdapterListener(null);
    }

    @Override
    public void addDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener) {
        deviceDiscoveryListeners.add(deviceDiscoveryListener);
    }

    @Override
    public void removeDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener) {
        deviceDiscoveryListeners.remove(deviceDiscoveryListener);
    }

    @Override
    public boolean isConnected(URL url) {
        return getDeviceGovernor(url).isConnected();
    }

    @Override
    public boolean getConnectionControl(URL url) {
        return getDeviceGovernor(url).isConnectionEnabled();
    }

    @Override
    public void setConnectionControl(URL url, boolean connected) {
        getDeviceGovernor(url).setConnectionEnabled(connected);
    }

    @Override
    public boolean isBlocked(URL url) {
        return getDeviceGovernor(url).isBlocked();
    }

    @Override
    public boolean getBlockedControl(URL url) {
        return getDeviceGovernor(url).getBlockedControl();
    }

    @Override
    public void setBlockedControl(URL url, boolean blocked) {
        getDeviceGovernor(url).setBlockedControl(blocked);
    }

    @Override
    public boolean isOnline(URL url) {
        return getDeviceGovernor(url).isOnline();
    }

    @Override
    public short getRSSI(URL url) {
        return getDeviceGovernor(url).getRSSI();
    }

    @Override
    public boolean isAdapterPowered(URL url) {
        return getAdapterGovernor(url).isPowered();
    }

    @Override
    public boolean getAdapterPoweredControl(URL url) {
        return getAdapterGovernor(url).getPoweredControl();
    }

    @Override
    public void setAdapterPoweredControl(URL url, boolean power) {
        getAdapterGovernor(url).setPoweredControl(power);
    }

    @Override
    public boolean isAdapterDiscovering(URL url) {
        return getAdapterGovernor(url).isDiscovering();
    }

    @Override
    public boolean getAdapterDiscoveringControl(URL url) {
        return getAdapterGovernor(url).getDiscoveringControl();
    }

    @Override
    public void setAdapterDiscoveringControl(URL url, boolean discovering) {
        getAdapterGovernor(url).setDiscoveringControl(discovering);
    }

    @Override
    public void setAdapterAlias(URL url, String alias) {
        getAdapterGovernor(url).setAlias(alias);
    }

    @Override
    public void disposeBluetoothObject(URL url) {
        disposeGovernor(url);
    }

    private void disposeGovernor(URL url) {
        synchronized (governors) {
            if (governors.containsKey(url)) {
                governors.get(url).dispose();
                synchronized (governorFutures) {
                    if (governorFutures.containsKey(url)) {
                        governorFutures.get(url).cancel(true);
                        governorFutures.remove(url);
                    }
                }
                governors.remove(url);
            }
        }
    }

    private DeviceGovernor getDeviceGovernor(URL url) {
        return (DeviceGovernor) getGovernor(url);
    }
    private AdapterGovernor getAdapterGovernor(URL url) {
        return (AdapterGovernor) getGovernor(url);
    }

    @Override
    public void addGenericBluetoothDeviceListener(URL url, GenericBluetoothDeviceListener listener) {
        getDeviceGovernor(url).setGenericBluetoothDeviceListener(listener);
    }

    @Override
    public void removeGenericBluetoothDeviceListener(URL url) {
        getDeviceGovernor(url).setGenericBluetoothDeviceListener(null);
    }

    @Override
    public void addBluetoothSmartDeviceListener(URL url, BluetoothSmartDeviceListener listener) {
        getDeviceGovernor(url).addBluetoothSmartDeviceListener(listener);
    }

    @Override
    public void removeBluetoothSmartDeviceListener(URL url, BluetoothSmartDeviceListener listener) {
        getDeviceGovernor(url).removeBluetoothSmartDeviceListener(listener);
    }

    @Override
    public void addCharacteristicListener(URL url, CharacteristicListener characteristicListener) {
        getDeviceGovernor(url).addCharacteristicListener(url, characteristicListener);
    }

    @Override
    public void removeCharacteristicListener(URL url) {
        getDeviceGovernor(url).removeCharacteristicListener(url);
    }

    @Override
    public byte[] readCharacteristic(URL url) {
        return getDeviceGovernor(url).read(url);
    }

    @Override
    public boolean writeCharacteristic(URL url, byte[] data) {
        return getDeviceGovernor(url).write(url, data);
    }

    @Override
    public void dispose() {
        logger.info("Disposing Bluetooth service");

        singleThreadScheduler.shutdown();
        scheduler.shutdown();
        if (discoveryFuture != null) {
            discoveryFuture.cancel(true);
        }
        for (ScheduledFuture future : Sets.newHashSet(governorFutures.values())) {
            future.cancel(true);
        }
        deviceDiscoveryListeners.clear();

        synchronized (governors) {
            for (BluetoothObjectGovernor governor : governors.values()) {
                try {
                    governor.dispose();
                } catch (Exception ex) {
                    logger.error("Could not dispose governor: " + governor.getURL());
                }
            }
            governors.clear();
        }
        logger.info("Bluetooth service has been disposed");
    }

    private BluetoothObjectGovernor getGovernor(URL url) {
        URL governorURL = url.getDeviceURL();
        synchronized (governors) {
            if (!governors.containsKey(governorURL)) {
                BluetoothObjectGovernor governor = url.getDeviceAddress() != null ?
                        new DeviceGovernor(governorURL) : new AdapterGovernor(governorURL);
                governors.put(governorURL, governor);
                governorFutures.put(governorURL,
                        scheduler.scheduleAtFixedRate((Runnable) () -> update(governor), 1, 5, TimeUnit.SECONDS));
                return governor;
            }
            return governors.get(governorURL);
        }
    }

    private void notifyDeviceDiscovered(DiscoveredDevice device) {
        for (DeviceDiscoveryListener deviceDiscoveryListener : Lists.newArrayList(deviceDiscoveryListeners)) {
            try {
                deviceDiscoveryListener.discovered(device);
            } catch (Exception ex) {
                logger.error("Discovery listener error", ex);
            }
        }
    }

    private DiscoveredDevice getDiscoveredDevice(BluetoothDevice device) {
        return new DiscoveredDevice(new URL(device.getAdapter().getAddress(), device.getAddress()),
                device.getName(), device.getAlias(), device.getRSSI(),
                device.getBluetoothClass());
    }

    private DiscoveredDevice getDiscoveredAdapter(BluetoothAdapter adapter) {
        return new DiscoveredDevice(new URL(adapter.getAddress(), null), adapter.getName(), adapter.getAlias());
    }

    private void notifyDeviceLost(URL url) {
        logger.info("Device has been lost: " + url.getAdapterAddress() + " - " + url.getDeviceAddress());
        for (DeviceDiscoveryListener deviceDiscoveryListener : Lists.newArrayList(deviceDiscoveryListeners)) {
            try {
                deviceDiscoveryListener.lost(url);
            } catch (Throwable ex) {
                logger.error("Device listener error", ex);
            }
        }
        try {
            getDeviceGovernor(url).reset();
        } catch (Throwable ex) {
            logger.warn("Could not reset device governor", ex);
        }
    }

    private void update(BluetoothObjectGovernor governor) {
        try {
            governor.update();
        } catch (Throwable ex) {
            logger.error("Could not update governor: " + governor.getURL(), ex);
        }
    }

    private class DiscoveryJob implements Runnable {

        private Set<DiscoveredDevice> discovered = new HashSet<>();

        @Override
        public void run() {
            try {
                //BluetoothManager.getBluetoothManager().startDiscovery();
                List<BluetoothDevice> list = tinyb.BluetoothManager.getBluetoothManager().getDevices();
                if (list == null) {
                    return;
                }

                Set<DiscoveredDevice> newDiscovery = new HashSet<>();
                for (BluetoothDevice device : list) {
                    short rssi = device.getRSSI();
                    if (rssi == 0) {
                        continue;
                    }
                    DiscoveredDevice discoveredDevice = getDiscoveredDevice(device);
                    notifyDeviceDiscovered(discoveredDevice);
                    newDiscovery.add(discoveredDevice);

                }
                for (BluetoothAdapter adapter : tinyb.BluetoothManager.getBluetoothManager().getAdapters()) {
                    DiscoveredDevice discoveredAdapter = getDiscoveredAdapter(adapter);
                    notifyDeviceDiscovered(discoveredAdapter);
                    newDiscovery.add(discoveredAdapter);
                }
                for (DiscoveredDevice lost : Sets.difference(discovered, newDiscovery)) {
                    notifyDeviceLost(lost.getURL());
                }
                discovered.clear();
                discovered.addAll(newDiscovery);
            } catch (Exception ex) {
                logger.error("Discovery job error", ex);
            }
        }
    }

}
