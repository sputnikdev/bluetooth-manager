package org.sputnikdev.bluetooth.manager.impl.tinyb;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.impl.Notification;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothDevice;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothGattService;
import tinyb.BluetoothNotification;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor({"tinyb.BluetoothManager", "tinyb.BluetoothObject"})
public class TinyBCharacteristicTest {

    private static final String ADAPTER_MAC = "11:22:33:44:55:66";
    private static final String DEVICE_MAC = "12:34:56:78:90:12";
    private static final String SERVICE_UUID = "f000aa11-0451-4000-b000-000000000000";
    private static final String CHARACTERISTIC_UUID = "a000aa11-0451-4000-b000-000000000000";
    private static final String[] FLAGS = {"read", "write", "notify"};
    private static final boolean NOTIFYING = true;
    private static final byte[] VALUE = {1, 2, 3, 4};

    @Mock
    private BluetoothAdapter bluetoothAdapter;
    @Mock
    private BluetoothDevice bluetoothDevice;
    @Mock
    private BluetoothGattService bluetoothGattService;
    @Mock
    private BluetoothGattCharacteristic bluetoothGattCharacteristic;

    @InjectMocks
    private TinyBCharacteristic tinyBCharacteristic;

    @Before
    public void setUp() {
        when(bluetoothAdapter.getAddress()).thenReturn(ADAPTER_MAC);
        when(bluetoothDevice.getAdapter()).thenReturn(bluetoothAdapter);
        when(bluetoothDevice.getAddress()).thenReturn(DEVICE_MAC);
        when(bluetoothGattService.getUUID()).thenReturn(SERVICE_UUID);
        when(bluetoothGattService.getDevice()).thenReturn(bluetoothDevice);
        when(bluetoothGattCharacteristic.getUUID()).thenReturn(CHARACTERISTIC_UUID);
        when(bluetoothGattCharacteristic.getService()).thenReturn(bluetoothGattService);
        when(bluetoothGattCharacteristic.getFlags()).thenReturn(FLAGS);
        when(bluetoothGattCharacteristic.getNotifying()).thenReturn(NOTIFYING);
        when(bluetoothGattCharacteristic.readValue()).thenReturn(VALUE);
        when(bluetoothGattCharacteristic.writeValue(VALUE)).thenReturn(true);
    }

    @Test
    public void testGetURL() throws Exception {
        assertEquals(new URL(ADAPTER_MAC, DEVICE_MAC, SERVICE_UUID, CHARACTERISTIC_UUID),
                tinyBCharacteristic.getURL());
        verify(bluetoothAdapter, times(1)).getAddress();
        verify(bluetoothDevice, times(1)).getAddress();
        verify(bluetoothDevice, times(1)).getAdapter();
        verify(bluetoothGattService, times(1)).getUUID();
        verify(bluetoothGattService, times(1)).getDevice();
        verify(bluetoothGattCharacteristic, times(1)).getUUID();
        verify(bluetoothGattCharacteristic, times(1)).getService();
    }

    @Test
    public void testGetFlags() throws Exception {
        assertArrayEquals(FLAGS, tinyBCharacteristic.getFlags());
        verify(bluetoothGattCharacteristic, times(1)).getFlags();
    }

    @Test
    public void testIsNotifying() throws Exception {
        assertEquals(NOTIFYING, tinyBCharacteristic.isNotifying());
        verify(bluetoothGattCharacteristic, times(1)).getNotifying();
    }

    @Test
    public void testReadValue() throws Exception {
        assertArrayEquals(VALUE, tinyBCharacteristic.readValue());
        verify(bluetoothGattCharacteristic, times(1)).readValue();
    }

    @Test
    public void testEnableValueNotifications() throws Exception {
        Notification<byte[]> notification = mock(Notification.class);
        ArgumentCaptor<BluetoothNotification> captor = ArgumentCaptor.forClass(BluetoothNotification.class);
        doNothing().when(bluetoothGattCharacteristic).enableValueNotifications(captor.capture());

        tinyBCharacteristic.enableValueNotifications(notification);

        verify(bluetoothGattCharacteristic, times(1)).enableValueNotifications(captor.getValue());
        verifyNoMoreInteractions(bluetoothDevice, notification);

        captor.getValue().run(VALUE);
        verify(notification, times(1)).notify(VALUE);
    }

    @Test
    public void testDisableValueNotifications() throws Exception {
        tinyBCharacteristic.disableValueNotifications();
        verify(bluetoothGattCharacteristic, times(1)).disableValueNotifications();
    }

    @Test
    public void testWriteValue() throws Exception {
        assertTrue(tinyBCharacteristic.writeValue(VALUE));
        verify(bluetoothGattCharacteristic, times(1)).writeValue(VALUE);
    }
}
