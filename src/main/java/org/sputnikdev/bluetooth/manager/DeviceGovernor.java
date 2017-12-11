package org.sputnikdev.bluetooth.manager;

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

import org.sputnikdev.bluetooth.Filter;
import org.sputnikdev.bluetooth.URL;

import java.util.List;
import java.util.Map;


/**
 * Bluetooth device governor.
 * 
 * @author Vlad Kolotov
 */
public interface DeviceGovernor extends BluetoothGovernor {

    /**
     * Returns bluetooth class of the device.
     * 
     * @return bluetooth class
     * @throws NotReadyException if the bluetooth device is not ready
     */
    int getBluetoothClass() throws NotReadyException;

    /**
     * Checks whether the device supports BLE protocol.
     * 
     * @return true if the deice is a BLE device
     * @throws NotReadyException if the bluetooth device is not ready 
     */
    boolean isBleEnabled() throws NotReadyException;

    /**
     * Returns name of the device.
     * @return name of the adapter
     * @throws NotReadyException if the device is not ready
     */
    String getName() throws NotReadyException;

    /**
     *  Returns alias of the device.
     * @return alias of the device
     * @throws NotReadyException if the device is not ready
     */
    String getAlias() throws NotReadyException;

    /**
     * Sets alias for the device.
     * @param alias new alias
     */
    void setAlias(String alias) throws NotReadyException;

    /**
     * Returns display name of the device.
     * @return display name of the device
     * @throws NotReadyException if the device object is not ready
     */
    String getDisplayName() throws NotReadyException;

    /**
     * Checks whether the device is connected.
     * @return true if the device is connected, false otherwise
     * @throws NotReadyException if the device object is not ready
     */
    boolean isConnected() throws NotReadyException;

    /**
     * Returns device connection control status.
     * @return device connection control status
     */
    boolean getConnectionControl();

    /**
     * Sets device connection control status.
     * @param connected device connection control status
     */
    void setConnectionControl(boolean connected);

    /**
     * Checks whether the device is blocked.
     * @return true if the device is blocked, false otherwise
     * @throws NotReadyException if the device object is not ready
     */
    boolean isBlocked() throws NotReadyException;

    /**
     * Returns device blocked control status.
     *
     * @return device blocked control status
     */
    boolean getBlockedControl();

    /**
     * Sets device blocked control status.
     *
     * @param blocked a new blocked control status
     */
    void setBlockedControl(boolean blocked);

    /**
     * Checks whether the device is online.
     * A device is "online" if the device has shown its activity (see {@link BluetoothGovernor#getLastActivity()})
     * within configured "online timeout" setting (see {@link #getOnlineTimeout()}).
     * @return true if online, false otherwise
     */
    boolean isOnline();

    /**
     * Returns the device online timeout in seconds (see {@link #isOnline()}).
     * @return online timeout in seconds
     */
    int getOnlineTimeout();

    /**
     * Sets the device online timeout in seconds (see {@link #isOnline()}).
     * @param onlineTimeout a new value for the device online timeout
     */
    void setOnlineTimeout(int onlineTimeout);

    /**
     * Returns device RSSI.
     * @return device RSSI
     * @throws NotReadyException if the device object is not ready
     */
    short getRSSI() throws NotReadyException;

    /**
     * Enables/disables RSSI filtering.
     * Default implementation is Kalman filter
     * @param enabled if the filter is null, filtering disabled
     */
    void setRssiFilter(Filter<Short> filter);

    /**
     * Returns RSSI filter.
     * @return RSSI filter
     */
    Filter<Short> getRssiFilter();

    /**
     * Checks whether RSSI filtering is enabled.
     * @return true if enabled, false oherwise
     */
    boolean isRssiFilteringEnabled();

    /**
     * Enables/disables RSSI filtering (a filter must be set before).
     * @param enabled if true, disabled otherwise
     */
    void setRssiFilteringEnabled(boolean enabled);

    /**
     * Sets RSSI reporting rate (in milliseconds). RSSI is not reported more often than this value.
     * If is set to 0, then RSSI is reported unconditionally.
     * @param rate RSSI reporting rate
     */
    void setRssiReportingRate(long rate);

    /**
     * Returns RSSI reporting rate (in mlliseconds). If RSSI equals to 0, then RSSI is reported unconditionally.
     * @param rate RSSI reporting rate
     */
    long getRssiReportingRate();

    /**
     * Register a new Bluetooth Smart device listener.
     * @param listener a new Bluetooth Smart device listener
     */
    void addBluetoothSmartDeviceListener(BluetoothSmartDeviceListener listener);

    /**
     * Unregister a Bluetooth Smart device listener.
     * @param listener a previously registered listener
     */
    void removeBluetoothSmartDeviceListener(BluetoothSmartDeviceListener listener);

    /**
     * Registers a new Generic Bluetooth device listener
     * @param listener a new Generic Bluetooth device listener
     */
    void addGenericBluetoothDeviceListener(GenericBluetoothDeviceListener listener);

    /**
     * Unregisters a Generic Bluetooth device listener.
     * @param listener a previously registered listener
     */
    void removeGenericBluetoothDeviceListener(GenericBluetoothDeviceListener listener);

    /**
     * Returns a map of services to their characteristics.
     *
     * @return a map of services to their characteristics
     * @throws NotReadyException if the device object is not ready
     */
    Map<URL, List<CharacteristicGovernor>> getServicesToCharacteristicsMap() throws NotReadyException;

    /**
     * Returns a list of characteristic URLs of the device.
     * @return a list of characteristic URLs of the device
     * @throws NotReadyException if the device object is not ready
     */
    List<URL> getCharacteristics() throws NotReadyException;

    /**
     * Returns a list of characteristic governors associated to the device.
     * @return a list of characteristic governors associated to the device
     * @throws NotReadyException if the device object is not ready
     */
    List<CharacteristicGovernor> getCharacteristicGovernors() throws NotReadyException;

}
