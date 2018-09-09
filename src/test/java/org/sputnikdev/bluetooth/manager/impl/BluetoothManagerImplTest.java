package org.sputnikdev.bluetooth.manager.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.transport.Adapter;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObject;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObjectFactory;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.Device;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class BluetoothManagerImplTest {

    private static final String TINYB_PROTOCOL_NAME = "tinyb";
    private static final URL TINYB_ADAPTER_URL = new URL("tinyb:/11:22:33:44:55:66");
    private static final URL TINYB_DEVICE_URL = TINYB_ADAPTER_URL.copyWithDevice("12:34:56:78:90:12");
    private static final URL TINYB_CHARACTERISTIC_URL =
            TINYB_DEVICE_URL.copyWith("0000180f-0000-1000-8000-00805f9b34fb", "00002a19-0000-1000-8000-00805f9b34fb");

    private static final String DBUS_PROTOCOL_NAME = "dbus";
    private static final URL DBUS_ADAPTER_URL = new URL("dbus:/77:22:33:44:55:66");
    private static final URL DBUS_DEVICE_URL = DBUS_ADAPTER_URL.copyWithDevice("12:34:56:78:90:12");
    private static final URL DBUS_CHARACTERISTIC_URL =
            DBUS_DEVICE_URL.copyWith("0000180f-0000-1000-8000-00805f9b34fb", "00002a19-0000-1000-8000-00805f9b34fb");

    @Mock
    private BluetoothObjectFactory tinybObjectFactory;
    @Mock
    private BluetoothObjectFactory dbusObjectFactory;

    @Mock
    private Adapter tinybAdapter;
    @Mock
    private Device tinybDevice;
    @Mock
    private Characteristic tinybCharacteristic;

    @Mock
    private Adapter dbusAdapter;
    @Mock
    private Device dbusDevice;
    @Mock
    private Characteristic dbusCharacteristic;

    @InjectMocks
    private BluetoothManagerImpl bluetoothManager = new BluetoothManagerImpl() {
        @Override BluetoothObjectGovernor createGovernor(URL url) {
            return spy(super.createGovernor(url));
        }
    };

    @Before
    public void setUp() throws InterruptedException {
        when(tinybObjectFactory.getProtocolName()).thenReturn(TINYB_PROTOCOL_NAME);
        when(dbusObjectFactory.getProtocolName()).thenReturn(DBUS_PROTOCOL_NAME);

        when(tinybObjectFactory.getAdapter(TINYB_ADAPTER_URL)).thenReturn(tinybAdapter);
        when(tinybObjectFactory.getAdapter(TINYB_ADAPTER_URL.copyWithProtocol(null))).thenReturn(tinybAdapter);
        when(tinybObjectFactory.getDevice(TINYB_DEVICE_URL)).thenReturn(tinybDevice);
        when(tinybObjectFactory.getDevice(TINYB_DEVICE_URL.copyWithProtocol(null))).thenReturn(tinybDevice);
        when(tinybObjectFactory.getCharacteristic(TINYB_CHARACTERISTIC_URL)).thenReturn(tinybCharacteristic);

        when(dbusObjectFactory.getAdapter(DBUS_ADAPTER_URL)).thenReturn(dbusAdapter);
        when(dbusObjectFactory.getAdapter(DBUS_ADAPTER_URL.copyWithProtocol(null))).thenReturn(dbusAdapter);
        when(dbusObjectFactory.getDevice(DBUS_DEVICE_URL)).thenReturn(dbusDevice);
        when(dbusObjectFactory.getDevice(DBUS_DEVICE_URL.copyWithProtocol(null))).thenReturn(dbusDevice);
        when(dbusObjectFactory.getCharacteristic(DBUS_CHARACTERISTIC_URL)).thenReturn(dbusCharacteristic);

        DiscoveredAdapter tinyBDiscoveredAdapter = mock(DiscoveredAdapter.class);
        when(tinyBDiscoveredAdapter.getURL()).thenReturn(TINYB_ADAPTER_URL);
        DiscoveredAdapter dbusDiscoveredAdapter = mock(DiscoveredAdapter.class);
        when(dbusDiscoveredAdapter.getURL()).thenReturn(DBUS_ADAPTER_URL);

        when(tinybObjectFactory.getDiscoveredAdapters()).thenReturn(Collections.singleton(tinyBDiscoveredAdapter));
        when(dbusObjectFactory.getDiscoveredAdapters()).thenReturn(Collections.singleton(dbusDiscoveredAdapter));

        when(tinybAdapter.getURL()).thenReturn(TINYB_ADAPTER_URL);
        when(dbusAdapter.getURL()).thenReturn(DBUS_ADAPTER_URL);
        when(tinybDevice.getURL()).thenReturn(TINYB_DEVICE_URL);
        when(dbusDevice.getURL()).thenReturn(DBUS_DEVICE_URL);
        when(tinybCharacteristic.getURL()).thenReturn(TINYB_CHARACTERISTIC_URL);
        when(dbusCharacteristic.getURL()).thenReturn(DBUS_CHARACTERISTIC_URL);

        when(tinybAdapter.isPowered()).thenReturn(true);
        when(dbusAdapter.isPowered()).thenReturn(true);

        bluetoothManager.registerFactory(tinybObjectFactory);

        bluetoothManager.start(true);

        bluetoothManager.registerFactory(dbusObjectFactory);

        Set discoveredAdapters = bluetoothManager.getDiscoveredAdapters();
        while (discoveredAdapters.size() != 2) {
            Thread.sleep(10);
        }
    }

    @Test
    public void testGetAdapterNoProtocol() throws Exception {
        assertGetBluetoothObjectNoProtocol(tinybAdapter, TINYB_ADAPTER_URL);
    }

    @Test
    public void testGetDeviceNoProtocol() throws Exception {
        assertGetBluetoothObjectNoProtocol(tinybDevice, TINYB_DEVICE_URL);
    }

    @Test
    public void testGetCharacteristicNoProtocol() throws Exception {
        assertGetBluetoothObjectNoProtocol(tinybCharacteristic, TINYB_CHARACTERISTIC_URL);
    }

    @Test
    public void testGetBluetoothObjectWithProtocol() throws Exception {
        // easy case when URL specifies protocol name
        BluetoothObject bluetoothObject = bluetoothManager.getBluetoothObject(TINYB_ADAPTER_URL);
        assertEquals(tinybAdapter, bluetoothObject);
        bluetoothObject = bluetoothManager.getBluetoothObject(TINYB_DEVICE_URL);
        assertEquals(tinybDevice, bluetoothObject);
        bluetoothObject = bluetoothManager.getBluetoothObject(TINYB_CHARACTERISTIC_URL);
        assertEquals(tinybCharacteristic, bluetoothObject);
    }

    @Test
    public void testGetUnknownAdapter() throws Exception {
        assertGetBluetoothObjectUnknownAdapter(TINYB_ADAPTER_URL.copyWithAdapter("11:22:33:44:55:67"));
    }

    @Test
    public void testGetDeviceObjectUnknownAdapter() throws Exception {
        assertGetBluetoothObjectUnknownAdapter(TINYB_DEVICE_URL.copyWithAdapter("11:22:33:44:55:67"));
    }

    @Test
    public void testGetCharacteristicObjectUnknownAdapter() throws Exception {
        assertGetBluetoothObjectUnknownAdapter(TINYB_CHARACTERISTIC_URL.copyWithAdapter("11:22:33:44:55:67"));
    }

    @Test
    public void testResetDescendantsTinyb() {
        assertResetGovernors(1, 0, new URL("tinyb://"));
    }

    @Test
    public void testResetDescendantsDbus() {
        assertResetGovernors(0, 1, new URL("dbus://"));
    }

    @Test
    public void testResetDescendantsRoot() {
        assertResetGovernors(1, 1, new URL("/"));
    }

    @Test
    public void testUnregisterFactory() throws Exception {
        AdapterGovernorImpl tinybAdapterGovernor = (AdapterGovernorImpl)
                bluetoothManager.getAdapterGovernor(TINYB_ADAPTER_URL);
        DeviceGovernorImpl tinybDeviceGovernor = (DeviceGovernorImpl)
                bluetoothManager.getDeviceGovernor(TINYB_DEVICE_URL);
        CharacteristicGovernorImpl tinybCharacteristicGovernor = (CharacteristicGovernorImpl)
                bluetoothManager.getCharacteristicGovernor(TINYB_CHARACTERISTIC_URL);
        AdapterGovernorImpl dbusAdapterGovernor = (AdapterGovernorImpl)
                bluetoothManager.getAdapterGovernor(DBUS_ADAPTER_URL);
        DeviceGovernorImpl dbusDeviceGovernor = (DeviceGovernorImpl)
                bluetoothManager.getDeviceGovernor(DBUS_DEVICE_URL);
        CharacteristicGovernorImpl dbusCharacteristicGovernor = (CharacteristicGovernorImpl)
                bluetoothManager.getCharacteristicGovernor(DBUS_CHARACTERISTIC_URL);

        bluetoothManager.updateDescendants(URL.ROOT);

        bluetoothManager.unregisterFactory(tinybObjectFactory);

        verify(tinybAdapterGovernor).reset(tinybAdapter);
        verify(tinybDeviceGovernor).reset(tinybDevice);
        verify(tinybCharacteristicGovernor).reset(tinybCharacteristic);
        verify(dbusAdapterGovernor, never()).reset(dbusAdapter);
        verify(dbusDeviceGovernor, never()).reset(dbusDevice);
        verify(dbusCharacteristicGovernor, never()).reset(dbusCharacteristic);
    }

    private void assertResetGovernors(int tinybExpectedInvocations, int dbusExpectedInvocations, URL url) {
        AdapterGovernorImpl tinybAdapterGovernor = (AdapterGovernorImpl)
                bluetoothManager.getAdapterGovernor(TINYB_ADAPTER_URL);
        DeviceGovernorImpl tinybDeviceGovernor = (DeviceGovernorImpl)
                bluetoothManager.getDeviceGovernor(TINYB_DEVICE_URL);
        CharacteristicGovernorImpl tinybCharacteristicGovernor = (CharacteristicGovernorImpl)
                bluetoothManager.getCharacteristicGovernor(TINYB_CHARACTERISTIC_URL);

        AdapterGovernorImpl dbusAdapterGovernor = (AdapterGovernorImpl)
                bluetoothManager.getAdapterGovernor(DBUS_ADAPTER_URL);
        DeviceGovernorImpl dbusDeviceGovernor = (DeviceGovernorImpl)
                bluetoothManager.getDeviceGovernor(DBUS_DEVICE_URL);
        CharacteristicGovernorImpl dbusCharacteristicGovernor = (CharacteristicGovernorImpl)
                bluetoothManager.getCharacteristicGovernor(DBUS_CHARACTERISTIC_URL);

        bluetoothManager.updateDescendants(URL.ROOT);

        bluetoothManager.resetDescendants(url);

        verify(tinybAdapterGovernor, times(tinybExpectedInvocations)).reset(tinybAdapter);
        verify(tinybDeviceGovernor, times(tinybExpectedInvocations)).reset(tinybDevice);
        verify(tinybCharacteristicGovernor, times(tinybExpectedInvocations)).reset(tinybCharacteristic);

        verify(dbusAdapterGovernor, times(dbusExpectedInvocations)).reset(dbusAdapter);
        verify(dbusDeviceGovernor, times(dbusExpectedInvocations)).reset(dbusDevice);
        verify(dbusCharacteristicGovernor, times(dbusExpectedInvocations)).reset(dbusCharacteristic);

        reset(tinybAdapterGovernor, tinybDeviceGovernor, tinybCharacteristicGovernor,
                dbusAdapterGovernor, dbusDeviceGovernor, dbusCharacteristicGovernor);
    }

    private void assertGetBluetoothObjectUnknownAdapter(URL url) throws Exception {
        // easy case when URL specifies protocol name
        BluetoothObject bluetoothObject = bluetoothManager.getBluetoothObject(url);
        assertNull(bluetoothObject);

        bluetoothObject = bluetoothManager.getBluetoothObject(url.copyWithProtocol(null));
        assertNull(bluetoothObject);
    }

    private void assertGetBluetoothObjectNoProtocol(BluetoothObject expected, URL url) {
        // check if and bluetoothObject can be found even if bluetoothObject URL does not specify any protocol
        BluetoothObject bluetoothObject = bluetoothManager.getBluetoothObject(url.copyWithProtocol(null));
        assertEquals(expected, bluetoothObject);
    }

}
