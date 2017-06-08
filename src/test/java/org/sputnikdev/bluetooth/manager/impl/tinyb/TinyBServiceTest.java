package org.sputnikdev.bluetooth.manager.impl.tinyb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.impl.Characteristic;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothDevice;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothGattService;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor({"tinyb.BluetoothManager", "tinyb.BluetoothObject"})
public class TinyBServiceTest {

    private static final String ADAPTER_MAC = "11:22:33:44:55:66";
    private static final String DEVICE_MAC = "12:34:56:78:90:12";
    private static final String SERVICE_UUID = "f000aa11-0451-4000-b000-000000000000";
    private static final String CHARACTERISTIC_1_UUID = "a000aa11-0451-4000-b000-000000000000";
    private static final String CHARACTERISTIC_2_UUID = "a100aa11-0451-4000-b000-000000000000";

    @Mock
    private BluetoothGattService bluetoothGattService;
    @Mock
    private BluetoothDevice bluetoothDevice;
    @Mock
    private BluetoothAdapter bluetoothAdapter;

    @InjectMocks
    private TinyBService tinyBService;

    @Before
    public void setUp() {
        when(bluetoothAdapter.getAddress()).thenReturn(ADAPTER_MAC);
        when(bluetoothDevice.getAddress()).thenReturn(DEVICE_MAC);
        when(bluetoothGattService.getUUID()).thenReturn(SERVICE_UUID);

        when(bluetoothGattService.getDevice()).thenReturn(bluetoothDevice);
        when(bluetoothDevice.getAdapter()).thenReturn(bluetoothAdapter);

        List<BluetoothGattCharacteristic> chars = new ArrayList<>();
        BluetoothGattCharacteristic characteristic1 = mock(BluetoothGattCharacteristic.class);
        when(characteristic1.getUUID()).thenReturn(CHARACTERISTIC_1_UUID);
        when(characteristic1.getService()).thenReturn(bluetoothGattService);
        chars.add(characteristic1);
        BluetoothGattCharacteristic characteristic2 = mock(BluetoothGattCharacteristic.class);
        when(characteristic2.getUUID()).thenReturn(CHARACTERISTIC_2_UUID);
        when(characteristic2.getService()).thenReturn(bluetoothGattService);
        chars.add(characteristic2);
        when(bluetoothGattService.getCharacteristics()).thenReturn(chars);
    }

    @Test
    public void testGetURL() throws Exception {
        assertEquals(new URL(ADAPTER_MAC, DEVICE_MAC, SERVICE_UUID), tinyBService.getURL());
        verify(bluetoothAdapter, times(1)).getAddress();
        verify(bluetoothDevice, times(1)).getAddress();
        verify(bluetoothDevice, times(1)).getAdapter();
        verify(bluetoothGattService, times(1)).getUUID();
        verify(bluetoothGattService, times(1)).getDevice();
    }

    @Test
    public void testGetCharacteristics() throws Exception {
        List<Characteristic> characteristics = new ArrayList<>(tinyBService.getCharacteristics());

        Collections.sort(characteristics, new Comparator<Characteristic>() {
            @Override public int compare(Characteristic first, Characteristic second) {
                return first.getURL().compareTo(second.getURL());
            }
        });
        verify(bluetoothGattService, times(1)).getCharacteristics();

        assertEquals(2, characteristics.size());

        assertEquals(new URL(ADAPTER_MAC, DEVICE_MAC, SERVICE_UUID, CHARACTERISTIC_1_UUID),
                characteristics.get(0).getURL());
        assertEquals(new URL(ADAPTER_MAC, DEVICE_MAC, SERVICE_UUID, CHARACTERISTIC_2_UUID),
                characteristics.get(1).getURL());
    }
}
