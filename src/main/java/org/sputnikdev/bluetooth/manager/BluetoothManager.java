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

import java.util.Set;

import org.sputnikdev.bluetooth.URL;

/**
 *
 * @author Vlad Kolotov
 */
public interface BluetoothManager {

    Set<DiscoveredDevice> getDiscoveredDevices();

    BluetoothGovernor getGovernor(URL url);
    AdapterGovernor getAdapterGovernor(URL url);
    DeviceGovernor getDeviceGovernor(URL url);
    CharacteristicGovernor getCharacteristicGovernor(URL url);

    void startDiscovery();
    void stopDiscovery();
    void addDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener);
    void removeDiscoveryListener(DeviceDiscoveryListener deviceDiscoveryListener);

    void disposeGovernor(URL url);
    void dispose();

}
