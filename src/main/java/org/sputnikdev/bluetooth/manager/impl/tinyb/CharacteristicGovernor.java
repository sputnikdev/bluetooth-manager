package org.sputnikdev.bluetooth.manager.impl.tinyb;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.gattparser.URL;
import org.sputnikdev.bluetooth.manager.CharacteristicListener;
import tinyb.BluetoothDevice;
import tinyb.BluetoothException;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothGattService;
import tinyb.BluetoothManager;
import tinyb.BluetoothNotification;
import tinyb.BluetoothType;

public class CharacteristicGovernor extends BluetoothObjectGovernor<BluetoothGattCharacteristic> {

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
    BluetoothGattCharacteristic findBluetoothObject() {
        BluetoothDevice device = deviceGovernor.getBluetoothObject();
        BluetoothGattService service = (BluetoothGattService)
                BluetoothManager.getBluetoothManager().getObject(
                        BluetoothType.GATT_SERVICE, null, getURL().getServiceUUID(), device);
        return (BluetoothGattCharacteristic) BluetoothManager.getBluetoothManager().getObject(
                BluetoothType.GATT_CHARACTERISTIC, null, getURL().getCharacteristicUUID(), service);
    }

    @Override
    void disableNotifications(BluetoothGattCharacteristic characteristic) {
        logger.info("Disable characteristic notifications: " + getURL());
        if (characteristic.getNotifying()) {
            characteristic.disableValueNotifications();
            characteristicNotification = null;
        }
    }

    @Override
    void init(BluetoothGattCharacteristic characteristic) {
        enableNotification(characteristic);
    }

    @Override
    void dispose() { }

    @Override
    void updateState(BluetoothGattCharacteristic characteristic) { }

    void setCharacteristicListener(CharacteristicListener characteristicListener) {
        this.characteristicListener = characteristicListener;
    }

    byte[] read() {
        BluetoothGattCharacteristic characteristic = getBluetoothObject();
        if (characteristic == null) {
            throw new IllegalStateException("Characteristic governor is not initialized");
        }
        return characteristic.readValue();
    }

    boolean write(byte[] data) {
        BluetoothGattCharacteristic characteristic = getBluetoothObject();
        if (characteristic == null) {
            throw new IllegalStateException("Characteristic governor is not initialized");
        }
        return characteristic.writeValue(data);
    }

    private void enableNotification(BluetoothGattCharacteristic characteristic) {
        logger.info("Enable characteristic notifications: " + getURL());
        this.characteristicNotification = new CharacteristicNotification();
        if (canNotify(characteristic) && !characteristic.getNotifying()) {
            characteristic.enableValueNotifications(characteristicNotification);
        }
    }

    public static boolean canNotify(BluetoothGattCharacteristic characteristic) {
        List<String> flgs = Arrays.asList(characteristic.getFlags());
        return flgs.contains(NOTIFY_FLAG) || flgs.contains(INDICATE_FLAG);
    }

    private class CharacteristicNotification implements BluetoothNotification<byte[]> {
        @Override
        public void run(byte[] data) {
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
