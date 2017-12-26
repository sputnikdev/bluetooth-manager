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

import java.util.List;

import org.sputnikdev.bluetooth.URL;

/**
 *  A governor that manages Bluetooth adapter objects ({@link BluetoothGovernor}). Contains some "offline" and
 *  "online" methods see {@link BluetoothGovernor}.
 *
 * @author Vlad Kolotov
 */
public interface AdapterGovernor extends BluetoothGovernor {

    /**
     * Returns name of the adapter.
     * @return name of the adapter
     * @throws NotReadyException if the adapter is not ready
     */
    String getName() throws NotReadyException;

    /**
     * Returns alias of the adapter.
     * @return alias of the adapter
     */
    String getAlias() throws NotReadyException;

    /**
     * Sets alias for the adapter.
     * @param alias new alias
     */
    void setAlias(String alias) throws NotReadyException;

    /**
     * Returns display name of the adapter.
     * @return display name of the adapter
     * @throws NotReadyException if the adapter object is not ready
     */
    String getDisplayName() throws NotReadyException;

    /**
     * Returns adapter powered status.
     * @return powered status
     * @throws NotReadyException if the adapter object is not ready
     */
    boolean isPowered() throws NotReadyException;

    /**
     * Returns adapter powered control status.
     * @return powered control status
     */
    boolean getPoweredControl();

    /**
     * Sets adapter powered control status.
     * @param powered a new powered control status
     */
    void setPoweredControl(boolean powered);

    /**
     * Returns adapter discovering status.
     * @return adapter discovering status
     * @throws NotReadyException if the adapter object is not ready
     */
    boolean isDiscovering() throws NotReadyException;

    /**
     * Returns adapter discovering control status.
     * @return adapter discovering control status
     */
    boolean getDiscoveringControl();

    /**
     * Sets adapter discovering control status.
     * @param discovering a new adapter discovering control status
     */
    void setDiscoveringControl(boolean discovering);

    /**
     * Returns estimated (used defined) signal propagation exponent. It is mainly used in estimated distance
     * calculation between the adapter and its devices. This factor is specific to the environment
     * where the adapter is used, i.e. how efficient the signal passes through obsticles on its way.
     * Normally it ranges from 2.0 (outdoors, no obsticles) to 4.0 (indoors, walls and furniture).
     * @return signal propagation exponent
     */
    double getSignalPropagationExponent();

    /**
     * Sets estimated (used defined) signal propagation exponent. It is mainly used in estimated distance
     * calculation between the adapter and its devices. This factor is specific to the environment
     * where the adapter is used, i.e. how efficient the signal passes through obsticles on its way.
     * Normally it ranges from 2.0 (outdoors, no obsticles) to 4.0 (indoors, walls and furniture).
     * @param exponent signal propagation exponent
     */
    void setSignalPropagationExponent(double exponent);

    /**
     * Returns a list of discovered Bluetooth devices by the adapter.
     * @return a list of discovered Bluetooth devices by the adapter
     * @throws NotReadyException if the adapter object is not ready
     */
    List<URL> getDevices() throws NotReadyException;

    /**
     * Returns a list of discovered device governors by the adapter.
     * @return a list of discovered device governors by the adapter
     * @throws NotReadyException if the adapter object is not ready
     */
    List<DeviceGovernor> getDeviceGovernors() throws NotReadyException;

    void addAdapterListener(AdapterListener adapterListener);

    void removeAdapterListener(AdapterListener adapterListener);

}
