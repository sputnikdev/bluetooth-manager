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

import java.util.Set;

/**
 *
 * @author Vlad Kolotov
 */
public interface Characteristic extends BluetoothObject {

    Set<CharacteristicAccessType> getFlags();

    boolean isNotifying();

    void disableValueNotifications();

    byte[] readValue();

    boolean writeValue(byte[] data);

    void enableValueNotifications(Notification<byte[]> notification);

    boolean isNotificationConfigurable();
}
