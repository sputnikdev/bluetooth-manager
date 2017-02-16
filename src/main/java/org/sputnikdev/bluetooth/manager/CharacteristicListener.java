package org.sputnikdev.bluetooth.manager;

public interface CharacteristicListener {

    void changed(byte[] value);

}
