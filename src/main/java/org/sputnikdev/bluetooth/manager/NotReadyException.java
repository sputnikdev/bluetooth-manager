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


/**
 *
 * An exception class is used to signal that some operations could not be done due to device/governor state.
 * It this exception occurs, this means that a corresponding to a governor low level object is still not acquired.
 * See {@link BluetoothGovernor} for more info.
 *
 * @author Vlad Kolotov
 */
public class NotReadyException extends RuntimeException {

    /**
     * A constructor without message.
     */
    public NotReadyException() {
        super();
    }

    /**
     * A constructor with a message.
     * @param message a message
     */
    public NotReadyException(String message) {
        super(message);
    }
}
