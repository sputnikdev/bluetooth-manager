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
 * @author Vlad Kolotov
 */
public interface CharacteristicGovernor extends BluetoothGovernor {

    String READ_FLAG = "read";
    String NOTIFY_FLAG = "notify";
    String INDICATE_FLAG = "indicate";
    String WRITE_FLAG = "write";

    String[] getFlags() throws NotReadyException;

    boolean isReadable() throws NotReadyException;
    boolean isWritable() throws NotReadyException;
    boolean isNotifiable() throws NotReadyException;

    byte[] read() throws NotReadyException;
    boolean write(byte[] data) throws NotReadyException;

    void addValueListener(ValueListener valueListener);
    void removeValueListener(ValueListener valueListener);

}
