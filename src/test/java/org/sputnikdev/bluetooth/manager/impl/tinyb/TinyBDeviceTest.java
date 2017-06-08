package org.sputnikdev.bluetooth.manager.impl.tinyb;

import java.util.ArrayList;
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
import org.sputnikdev.bluetooth.manager.impl.Notification;
import org.sputnikdev.bluetooth.manager.impl.Service;
import tinyb.BluetoothAdapter;
import tinyb.BluetoothDevice;
import tinyb.BluetoothGattService;
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
public class TinyBDeviceTest {

    private static final String ADAPTER_MAC = "11:22:33:44:55:66";
    private static final String DEVICE_MAC = "12:34:56:78:90:12";
    private static final String ALIAS = "alias";
    private static final String NAME = "name";
    private static final int CLASS = 1;
    private static final boolean BLOCKED = true;
    private static final short RSSI = -80;
    private static final boolean CONNECTED = true;
    private static final boolean SERVICES_RESOLVED = true;

    private static final String SERVICE_1_UUID = "f000aa11-0451-4000-b000-000000000000";
    private static final String SERVICE_2_UUID = "f000aa12-0451-4000-b000-000000000000";

    @Mock
    private BluetoothAdapter bluetoothAdapter;
    @Mock
    private BluetoothDevice bluetoothDevice;

    @InjectMocks
    private TinyBDevice tinyBDevice;

    @Before
    public void setUp() {
        when(bluetoothAdapter.getAddress()).thenReturn(ADAPTER_MAC);
        when(bluetoothDevice.getAdapter()).thenReturn(bluetoothAdapter);
        when(bluetoothDevice.getAddress()).thenReturn(DEVICE_MAC);
        when(bluetoothDevice.getAlias()).thenReturn(ALIAS);
        when(bluetoothDevice.getName()).thenReturn(NAME);
        when(bluetoothDevice.getBluetoothClass()).thenReturn(CLASS);
        when(bluetoothDevice.getBlocked()).thenReturn(BLOCKED);
        when(bluetoothDevice.getRSSI()).thenReturn(RSSI);
        when(bluetoothDevice.getConnected()).thenReturn(CONNECTED);
        when(bluetoothDevice.getServicesResolved()).thenReturn(SERVICES_RESOLVED);

        List<BluetoothGattService> services = new ArrayList<>();
        BluetoothGattService service1 = mock(BluetoothGattService.class);
        when(service1.getUUID()).thenReturn(SERVICE_1_UUID);
        when(service1.getDevice()).thenReturn(bluetoothDevice);
        services.add(service1);
        BluetoothGattService service2 = mock(BluetoothGattService.class);
        when(service2.getUUID()).thenReturn(SERVICE_2_UUID);
        when(service2.getDevice()).thenReturn(bluetoothDevice);
        services.add(service2);
        when(bluetoothDevice.getServices()).thenReturn(services);
    }

    @Test
    public void testGetURL() throws Exception {
        assertEquals(new URL(ADAPTER_MAC, DEVICE_MAC), tinyBDevice.getURL());
        verify(bluetoothAdapter, times(1)).getAddress();
        verify(bluetoothDevice, times(1)).getAddress();
        verify(bluetoothDevice, times(1)).getAdapter();
    }

    @Test
    public void testGetAlias() throws Exception {
        assertEquals(ALIAS, tinyBDevice.getAlias());
        verify(bluetoothDevice, times(1)).getAlias();
    }

    @Test
    public void testGetName() throws Exception {
        assertEquals(NAME, tinyBDevice.getName());
        verify(bluetoothDevice, times(1)).getName();
    }

    @Test
    public void testGetBluetoothClass() throws Exception {
        assertEquals(CLASS, tinyBDevice.getBluetoothClass());
        verify(bluetoothDevice, times(1)).getBluetoothClass();
    }

    @Test
    public void testDisconnect() throws Exception {
        tinyBDevice.disconnect();
        verify(bluetoothDevice, times(1)).disconnect();
    }

    @Test
    public void testConnect() throws Exception {
        tinyBDevice.connect();
        verify(bluetoothDevice, times(1)).connect();
    }

    @Test
    public void testSetAlias() throws Exception {
        tinyBDevice.setAlias(ALIAS);
        verify(bluetoothDevice, times(1)).setAlias(ALIAS);
    }

    @Test
    public void testIsBlocked() throws Exception {
        assertEquals(BLOCKED, tinyBDevice.isBlocked());
        verify(bluetoothDevice, times(1)).getBlocked();
    }

    @Test
    public void testEnableBlockedNotifications() throws Exception {
        Notification<Boolean> notification = mock(Notification.class);
        ArgumentCaptor<BluetoothNotification> captor = ArgumentCaptor.forClass(BluetoothNotification.class);
        doNothing().when(bluetoothDevice).enableBlockedNotifications(captor.capture());

        tinyBDevice.enableBlockedNotifications(notification);

        verify(bluetoothDevice, times(1)).enableBlockedNotifications(captor.getValue());
        verifyNoMoreInteractions(bluetoothDevice, notification);

        captor.getValue().run(Boolean.TRUE);
        verify(notification, times(1)).notify(Boolean.TRUE);
    }

    @Test
    public void testDisableBlockedNotifications() throws Exception {
        tinyBDevice.disableBlockedNotifications();
        verify(bluetoothDevice, times(1)).disableBlockedNotifications();
    }

    @Test
    public void testSetBlocked() throws Exception {
        tinyBDevice.setBlocked(BLOCKED);
        verify(bluetoothDevice, times(1)).setBlocked(BLOCKED);
    }

    @Test
    public void testGetRSSI() throws Exception {
        assertEquals(RSSI, tinyBDevice.getRSSI());
        verify(bluetoothDevice, times(1)).getRSSI();
    }

    @Test
    public void testEnableRSSINotifications() throws Exception {
        Notification<Short> notification = mock(Notification.class);
        ArgumentCaptor<BluetoothNotification> captor = ArgumentCaptor.forClass(BluetoothNotification.class);
        doNothing().when(bluetoothDevice).enableRSSINotifications(captor.capture());

        tinyBDevice.enableRSSINotifications(notification);

        verify(bluetoothDevice, times(1)).enableRSSINotifications(captor.getValue());
        verifyNoMoreInteractions(bluetoothDevice, notification);

        captor.getValue().run(RSSI);
        verify(notification, times(1)).notify(RSSI);
    }

    @Test
    public void testDisableRSSINotifications() throws Exception {
        tinyBDevice.disableRSSINotifications();
        verify(bluetoothDevice, times(1)).disableRSSINotifications();
    }

    @Test
    public void testIsConnected() throws Exception {
        assertEquals(CONNECTED, tinyBDevice.isConnected());
        verify(bluetoothDevice, times(1)).getConnected();
    }

    @Test
    public void testEnableConnectedNotifications() throws Exception {
        Notification<Boolean> notification = mock(Notification.class);
        ArgumentCaptor<BluetoothNotification> captor = ArgumentCaptor.forClass(BluetoothNotification.class);
        doNothing().when(bluetoothDevice).enableConnectedNotifications(captor.capture());

        tinyBDevice.enableConnectedNotifications(notification);

        verify(bluetoothDevice, times(1)).enableConnectedNotifications(captor.getValue());
        verifyNoMoreInteractions(bluetoothDevice, notification);

        captor.getValue().run(CONNECTED);
        verify(notification, times(1)).notify(CONNECTED);
    }

    @Test
    public void testDisableConnectedNotifications() throws Exception {
        tinyBDevice.disableConnectedNotifications();
        verify(bluetoothDevice, times(1)).disableConnectedNotifications();
    }

    @Test
    public void testIsServicesResolved() throws Exception {
        assertEquals(SERVICES_RESOLVED, tinyBDevice.isServicesResolved());
        verify(bluetoothDevice, times(1)).getServicesResolved();
    }

    @Test
    public void testEnableServicesResolvedNotifications() throws Exception {
        Notification<Boolean> notification = mock(Notification.class);
        ArgumentCaptor<BluetoothNotification> captor = ArgumentCaptor.forClass(BluetoothNotification.class);
        doNothing().when(bluetoothDevice).enableServicesResolvedNotifications(captor.capture());

        tinyBDevice.enableServicesResolvedNotifications(notification);

        verify(bluetoothDevice, times(1)).enableServicesResolvedNotifications(captor.getValue());
        verifyNoMoreInteractions(bluetoothDevice, notification);

        captor.getValue().run(SERVICES_RESOLVED);
        verify(notification, times(1)).notify(SERVICES_RESOLVED);
    }

    @Test
    public void testDisableServicesResolvedNotifications() throws Exception {
        tinyBDevice.disableServicesResolvedNotifications();
        verify(bluetoothDevice, times(1)).disableServicesResolvedNotifications();
    }

    @Test
    public void testGetServices() throws Exception {
        List<Service> services = new ArrayList<>(tinyBDevice.getServices());

        Collections.sort(services, new Comparator<Service>() {
            @Override public int compare(Service first, Service second) {
                return first.getURL().compareTo(second.getURL());
            }
        });
        verify(bluetoothDevice, times(1)).getServices();

        assertEquals(2, services.size());

        assertEquals(new URL(ADAPTER_MAC, DEVICE_MAC, SERVICE_1_UUID), services.get(0).getURL());
        assertEquals(new URL(ADAPTER_MAC, DEVICE_MAC, SERVICE_2_UUID), services.get(1).getURL());
    }
}
