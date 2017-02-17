package org.sputnikdev.bluetooth.manager.impl;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.gattparser.URL;
import org.sputnikdev.bluetooth.manager.CharacteristicListener;
import tinyb.BluetoothException;

class CharacteristicGovernor extends BluetoothObjectGovernor<Characteristic<?>> {

    private static final String NOTIFY_FLAG = "notify";
    private static final String INDICATE_FLAG = "indicate";


    private Logger logger = LoggerFactory.getLogger(CharacteristicGovernor.class);

    private final DeviceGovernor deviceGovernor;
    private CharacteristicListener characteristicListener;
    private CharacteristicNotification characteristicNotification;

    CharacteristicGovernor(DeviceGovernor deviceGovernor, URL url) {
        super(url);
        this.deviceGovernor = deviceGovernor;
    }

    @Override
    Characteristic findBluetoothObject() {
        return BluetoothObjectFactory.getDefault().getCharacteristic(getURL());
    }

    @Override
    void disableNotifications(Characteristic characteristic) {
        logger.info("Disable characteristic notifications: " + getURL());
        if (characteristic.isNotifying()) {
            characteristic.disableValueNotifications();
            characteristicNotification = null;
        }
    }

    @Override
    void init(Characteristic characteristic) {
        enableNotification(characteristic);
    }

    @Override
    void dispose() { }

    @Override
    void updateState(Characteristic characteristic) { }

    void setCharacteristicListener(CharacteristicListener characteristicListener) {
        this.characteristicListener = characteristicListener;
    }

    byte[] read() {
        Characteristic characteristic = getBluetoothObject();
        if (characteristic == null) {
            throw new IllegalStateException("Characteristic governor is not initialized");
        }
        return characteristic.readValue();
    }

    boolean write(byte[] data) {
        Characteristic characteristic = getBluetoothObject();
        if (characteristic == null) {
            throw new IllegalStateException("Characteristic governor is not initialized");
        }
        return characteristic.writeValue(data);
    }

    private void enableNotification(Characteristic characteristic) {
        logger.info("Enable characteristic notifications: " + getURL());
        this.characteristicNotification = new CharacteristicNotification();
        if (canNotify(characteristic) && !characteristic.isNotifying()) {
            characteristic.enableValueNotifications(characteristicNotification);
        }
    }

    public static boolean canNotify(Characteristic characteristic) {
        List<String> flgs = Arrays.asList(characteristic.getFlags());
        return flgs.contains(NOTIFY_FLAG) || flgs.contains(INDICATE_FLAG);
    }

    private class CharacteristicNotification implements Notification<byte[]> {
        @Override
        public void notify(byte[] data) {
            try {
                deviceGovernor.updateLastUpdated();
                CharacteristicListener listener = characteristicListener;
                if (listener != null) {
                    listener.changed(data);
                }
            } catch (BluetoothException ex) {
                logger.error("Execution error of a characteristic listener", ex);
            }
        }
    }

}
