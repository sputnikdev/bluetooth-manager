package org.sputnikdev.bluetooth.manager.impl.tinyb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.impl.Device;
import org.sputnikdev.bluetooth.manager.impl.Notification;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothDevice;
import tinyb.BluetoothNotification;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor({"tinyb.BluetoothManager", "tinyb.BluetoothObject"})
public class TinyBAdapterTest {

    private static final String MAC = "11:22:33:44:55:66";
    private static final String ALIAS = "alias";
    private static final String NAME = "name";
    private static final boolean POWERED = true;
    private static final boolean DISCOVERING = true;

    private static final String DEVICE_1_MAC = "12:34:56:67:89:11";
    private static final String DEVICE_2_MAC = "44:33:22:11:77:88";
    private static final String DEVICE_MAC_INACTIVE = "66:77:88:99:77:88";

    @Mock
    private BluetoothAdapter bluetoothAdapter;

    @InjectMocks
    private TinyBAdapter tinyBAdapter;

    @Before
    public void setUp() {
        when(bluetoothAdapter.getAddress()).thenReturn(MAC);
        when(bluetoothAdapter.getAlias()).thenReturn(ALIAS);
        when(bluetoothAdapter.getName()).thenReturn(NAME);
        when(bluetoothAdapter.getPowered()).thenReturn(POWERED);
        when(bluetoothAdapter.getDiscovering()).thenReturn(DISCOVERING);

        BluetoothDevice device1 = mock(BluetoothDevice.class);
        when(device1.getAddress()).thenReturn(DEVICE_1_MAC);
        when(device1.getRSSI()).thenReturn((short) -1);
        when(device1.getAdapter()).thenReturn(bluetoothAdapter);
        BluetoothDevice device2 = mock(BluetoothDevice.class);
        when(device2.getAddress()).thenReturn(DEVICE_2_MAC);
        when(device2.getRSSI()).thenReturn((short) -80);
        when(device2.getAdapter()).thenReturn(bluetoothAdapter);
        BluetoothDevice inactiveDevice = mock(BluetoothDevice.class);
        when(inactiveDevice.getAddress()).thenReturn(DEVICE_MAC_INACTIVE);
        when(inactiveDevice.getRSSI()).thenReturn((short) 0);
        when(inactiveDevice.getAdapter()).thenReturn(bluetoothAdapter);

        when(bluetoothAdapter.getDevices()).thenReturn(Arrays.asList(device1, device2, inactiveDevice));
    }

    @Test
    public void testGetURL() throws Exception {
        assertEquals(new URL(MAC, null), tinyBAdapter.getURL());
        verify(bluetoothAdapter, times(1)).getAddress();
    }

    @Test
    public void testGetAlias() throws Exception {
        assertEquals(ALIAS, tinyBAdapter.getAlias());
        verify(bluetoothAdapter, times(1)).getAlias();
    }

    @Test
    public void testGetName() throws Exception {
        assertEquals(NAME, tinyBAdapter.getName());
        verify(bluetoothAdapter, times(1)).getName();
    }

    @Test
    public void testSetAlias() throws Exception {
        tinyBAdapter.setAlias(ALIAS);
        verify(bluetoothAdapter, times(1)).setAlias(ALIAS);
    }

    @Test
    public void testIsPowered() throws Exception {
        assertEquals(POWERED, tinyBAdapter.isPowered());
        verify(bluetoothAdapter, times(1)).getPowered();
    }

    @Test
    public void testEnablePoweredNotifications() throws Exception {
        Notification<Boolean> notification = mock(Notification.class);
        ArgumentCaptor<BluetoothNotification> captor = ArgumentCaptor.forClass(BluetoothNotification.class);
        doNothing().when(bluetoothAdapter).enablePoweredNotifications(captor.capture());

        tinyBAdapter.enablePoweredNotifications(notification);

        verify(bluetoothAdapter, times(1)).enablePoweredNotifications(captor.getValue());
        verifyNoMoreInteractions(bluetoothAdapter, notification);

        captor.getValue().run(Boolean.TRUE);
        verify(notification, times(1)).notify(Boolean.TRUE);
    }

    @Test
    public void testDisablePoweredNotifications() throws Exception {
        tinyBAdapter.disablePoweredNotifications();
        verify(bluetoothAdapter, times(1)).disablePoweredNotifications();
    }

    @Test
    public void testSetPowered() throws Exception {
        tinyBAdapter.setPowered(POWERED);
        verify(bluetoothAdapter, times(1)).setPowered(POWERED);
    }

    @Test
    public void testIsDiscovering() throws Exception {
        assertEquals(DISCOVERING, tinyBAdapter.isDiscovering());
        verify(bluetoothAdapter, times(1)).getDiscovering();
    }

    @Test
    public void testEnableDiscoveringNotifications() throws Exception {
        Notification<Boolean> notification = mock(Notification.class);
        ArgumentCaptor<BluetoothNotification> captor = ArgumentCaptor.forClass(BluetoothNotification.class);
        doNothing().when(bluetoothAdapter).enableDiscoveringNotifications(captor.capture());

        tinyBAdapter.enableDiscoveringNotifications(notification);

        verify(bluetoothAdapter, times(1)).enableDiscoveringNotifications(captor.getValue());
        verifyNoMoreInteractions(bluetoothAdapter, notification);

        captor.getValue().run(Boolean.TRUE);
        verify(notification, times(1)).notify(Boolean.TRUE);
    }

    @Test
    public void testDisableDiscoveringNotifications() throws Exception {
        tinyBAdapter.disableDiscoveringNotifications();
        verify(bluetoothAdapter, times(1)).disableDiscoveringNotifications();
    }

    @Test
    public void testStartDiscovery() throws Exception {
        tinyBAdapter.startDiscovery();
        verify(bluetoothAdapter, times(1)).startDiscovery();
    }

    @Test
    public void testStopDiscovery() throws Exception {
        tinyBAdapter.stopDiscovery();
        verify(bluetoothAdapter, times(1)).stopDiscovery();
    }

    @Test
    public void testGetDevices() throws Exception {
        List<Device> devices = new ArrayList<>(tinyBAdapter.getDevices());

        Collections.sort(devices, new Comparator<Device>() {
            @Override public int compare(Device first, Device second) {
                return first.getURL().compareTo(second.getURL());
            }
        });
        verify(bluetoothAdapter, times(1)).getDevices();

        assertEquals(2, devices.size());

        assertEquals(new URL(MAC, DEVICE_1_MAC), devices.get(0).getURL());
        assertEquals(new URL(MAC, DEVICE_2_MAC), devices.get(1).getURL());
    }
}
