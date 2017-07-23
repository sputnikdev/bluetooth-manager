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

import org.sputnikdev.bluetooth.manager.BluetoothManager;

/**
 *
 * @author Vlad Kolotov
 */
public class BluetoothManagerFactory {

    private static BluetoothManager instance;

    private BluetoothManagerFactory() { }

    public static BluetoothManager getManager() {
        if (instance == null) {
            synchronized (BluetoothManager.class) {
                if (instance == null) {
                    instance = new BluetoothManagerImpl();
                }
            }
        }
        return instance;
    }

    public static void dispose(BluetoothManager bluetoothManager) {
        if (instance != null) {
            synchronized (BluetoothManager.class) {
                if (instance == bluetoothManager) {
                    instance = null;
                }
            }
        }
        bluetoothManager.dispose();
    }

}
