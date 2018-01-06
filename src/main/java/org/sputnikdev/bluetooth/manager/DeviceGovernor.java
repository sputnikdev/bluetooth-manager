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
     * Sets RSSI filter class. A new instnce is created by using reflection and a default constructior.
     * Default implementation is Kalman filter
     * @param enabled if the filter is null, filtering disabled
     */
    void setRssiFilter(Class<? extends Filter<Short>> filter);

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
     * Returns actual (manufacturer defined) TX power of the device. Some bluetooth devices do not adverise
     * its TX power, in this case the returning value is 0.
     * <br/>TX power is used in distance calculation ({@link #getEstimatedDistance()}).
     * @return actual TX power
     */
    short getTxPower();

    /**
     * Returns measured/estimated (user defined) TX power of the device that is measured 1 meter away from the adapter.
     * <br/>TX power is used in distance calculation ({@link #getEstimatedDistance()}).
     * @return measured TX power
     */
    short getMeasuredTxPower();

    /**
     * Sets measured/estimated (user defined) TX power of the device that is measured 1 meter away from the adapter.
     * TX power is used in distance calculation ({@link #getEstimatedDistance()}).
     * <br/>To measure TX power, step 1 meter away from the adapter and take note of RSSI value (this will be TX Power).
     * @param txPower TX power
     */
    void setMeasuredTxPower(short txPower);

    /**
     * Returns currently set the estimated (used defined) signal propagation exponent. It is used in distance
     * calculation ({@link #getEstimatedDistance()}). This factor is specific to the environment, i.e. how good or bad
     * the signal can penetrate through obsticles. Normally it ranges from 2.0 (outdoors, no obsticles)
     * to 4.0 (indoors, walls and furniture).
     * @return the signal propagation exponent
     */
    double getSignalPropagationExponent();

    /**
     * Sets the estimated (used defined) signal propagation exponent. It is used in distance
     * calculation ({@link #getEstimatedDistance()}). This factor is specific to the environment, i.e. how efficient
     * the signal passes through obsticles. Normally it ranges from 2.0 (outdoors, no obsticles)
     * to 4.0 (indoors, walls and furniture).
     * @param exponent the signal propagation exponent
     */
    void setSignalPropagationExponent(double exponent);

    /**
     * Returns estimated distance between this device and the adapter.
     * Either measured ({@link #setMeasuredTxPower(short)}) or actual ({@link #getTxPower()}) TX power must be
     * available for the estimation, otherwise the resulting value equals 0.
     * The calculation is based on the logarithmetic function: d = 10 ^ ((TxPower - RSSI) / 10n)
     * where n ({@link #getSignalPropagationExponent()}) is the signal propagation exponent
     * that ranges from 2 to 4 (environemnt specific factor, e.g. 2 outdoors -> 4 indoors)
     * @return estimated distance
     */
    double getEstimatedDistance();

    /**
     * Returns location of the device (closest adapter URL). If the governor represets a group of devices,
     * then the result depends on distance to closest adapter ({@link #getEstimatedDistance()}).
     * @return location of the device
     */
    URL getLocation();

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
     * Registers a new Generic Bluetooth device listener.
     * @param listener a new Generic Bluetooth device listener
     */
    void addGenericBluetoothDeviceListener(GenericBluetoothDeviceListener listener);

    /**
     * Unregisters a Generic Bluetooth device listener.
     * @param listener a previously registered listener
     */
    void removeGenericBluetoothDeviceListener(GenericBluetoothDeviceListener listener);

    /**
     * Checks whether services have been resolved.
     * @return true if services are resolved, false otherwise
     */
    boolean isServicesResolved();

    /**
     * Retunrs a list of resolved services. Null is returned if services are not resulved yet.
     * @return a list of resolved services
     */
    List<GattService> getResolvedServices() throws NotReadyException;

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
