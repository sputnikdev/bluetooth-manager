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
import java.util.Map;

import org.sputnikdev.bluetooth.URL;


/**
 *
 * @author Vlad Kolotov
 */
public interface DeviceGovernor extends BluetoothGovernor {

    int getBluetoothClass() throws NotReadyException;
    boolean isBleEnabled() throws NotReadyException;
    String getName() throws NotReadyException;
    String getAlias() throws NotReadyException;
    void setAlias(String alias) throws NotReadyException;
    String getDisplayName() throws NotReadyException;

    boolean isConnected() throws NotReadyException;
    boolean getConnectionControl();
    void setConnectionControl(boolean connected);

    boolean isBlocked() throws NotReadyException;
    boolean getBlockedControl();
    void setBlockedControl(boolean blocked);

    boolean isOnline();

    short getRSSI() throws NotReadyException;

    void addBluetoothSmartDeviceListener(BluetoothSmartDeviceListener listener);
    void removeBluetoothSmartDeviceListener(BluetoothSmartDeviceListener listener);

    void addGenericBluetoothDeviceListener(GenericBluetoothDeviceListener listener);
    void removeGenericBluetoothDeviceListener();

    Map<URL, List<CharacteristicGovernor>> getServicesToCharacteristicsMap() throws NotReadyException;

    List<URL> getCharacteristics() throws NotReadyException;

    List<CharacteristicGovernor> getCharacteristicGovernors() throws NotReadyException;

}
