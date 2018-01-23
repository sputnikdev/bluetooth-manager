package org.sputnikdev.bluetooth.manager.transport;

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

/**
 *
 * @author Vlad Kolotov
 */
public interface Device extends BluetoothObject {

    int getBluetoothClass();

    boolean disconnect();

    boolean connect();

    String getName();

    String getAlias();

    void setAlias(String alias);

    boolean isBlocked();

    boolean isBleEnabled();

    void enableBlockedNotifications(Notification<Boolean> notification);

    void disableBlockedNotifications();

    void setBlocked(boolean blocked);

    short getRSSI();

    short getTxPower();

    void enableRSSINotifications(Notification<Short> notification);

    void disableRSSINotifications();

    boolean isConnected();

    void enableConnectedNotifications(Notification<Boolean> notification);

    void disableConnectedNotifications();

    boolean isServicesResolved();

    void enableServicesResolvedNotifications(Notification<Boolean> notification);

    void disableServicesResolvedNotifications();

    List<Service> getServices();

    Map<String, byte[]> getServiceData();

    Map<Short, byte[]> getManufacturerData();

}
