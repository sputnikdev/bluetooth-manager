package org.sputnikdev.bluetooth.manager.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sputnikdev.bluetooth.RssiKalmanFilter;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothSmartDeviceListener;
import org.sputnikdev.bluetooth.manager.DeviceGovernor;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.GenericBluetoothDeviceListener;
import org.sputnikdev.bluetooth.manager.GovernorListener;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CombinedDeviceGovernorImplTest {

    private static final URL URL = new URL("/XX:XX:XX:XX:XX:XX/12:34:56:78:90:12");
    private static final URL TINYB_ADAPTER_URL = new URL("/11:22:33:44:55:66");
    private static final URL TINYB_DEVICE_URL = TINYB_ADAPTER_URL.copyWithDevice(URL.getDeviceAddress());
    private static final URL BLUEGIGA_ADAPTER_URL = new URL("/77:22:33:44:55:66");
    private static final URL BLUEGIGA_DEVICE_URL = BLUEGIGA_ADAPTER_URL.copyWithDevice(URL.getDeviceAddress());
    private static final String DEVICE_ALIAS = "Device Alias";
    private static final boolean CONNECTION_CONTROL = true;
    private static final boolean BLOCKED_CONTROL = false;
    private static final short MEASURED_TX_POWER = -77;
    private static final int ONLINE_TIMEOUT = 35;
    private static final boolean RSSI_FILTERING_ENABLED = true;
    private static final int RSSI_REPORTING_RATE = 2000;
    private static final double SIGNAL_PROPAGATION_EXPONENT = 1.5;
    private static final int BLUETOOTH_CLASS = 1;
    private static final boolean BLE_ENABLED = true;
    private static final String BLUEGIGA_DEVICE_NAME = "Bluegiga Device";
    private static final short TINYB_RSSI = -78;
    private static final short BLUEGIGA_RSSI = -87;
    public static final double TINYB_ESTIMATED_DISTANCE = 2.0;
    public static final double BLUEGIGA_ESTIMATED_DISTANCE = 1.0;

    @Mock
    private DeviceGovernor tinybGovernor;
    @Mock
    private DeviceGovernor bluegigaGovernor;

    private BluetoothManagerImpl bluetoothManager = mock(BluetoothManagerImpl.class);
    @Mock
    private DiscoveredAdapter tinybDiscoveredAdapter;
    @Mock
    private DiscoveredAdapter bluegigaDiscoveredAdapter;

    @Mock
    private GovernorListener governorListener;
    @Mock
    private BluetoothSmartDeviceListener bluetoothSmartDeviceListener;
    @Mock
    private GenericBluetoothDeviceListener genericBluetoothDeviceListener;

    @Captor
    private ArgumentCaptor<GovernorListener> tinybGovernorListenerDelegateCaptor;
    @Captor
    private ArgumentCaptor<GovernorListener> bluegigaGovernorListenerDelegateCaptor;
    @Captor
    private ArgumentCaptor<BluetoothSmartDeviceListener> tinybBluetoothSmartListenerDelegateCaptor;
    @Captor
    private ArgumentCaptor<BluetoothSmartDeviceListener> bluegigaBluetoothSmartListenerDelegateCaptor;
    @Captor
    private ArgumentCaptor<GenericBluetoothDeviceListener> tinybGenericBluetoothListenerDelegateCaptor;
    @Captor
    private ArgumentCaptor<GenericBluetoothDeviceListener> bluegigaGenericBluetoothListenerDelegateCaptor;

    @InjectMocks
    private CombinedDeviceGovernorImpl governor = new CombinedDeviceGovernorImpl(bluetoothManager, URL);

    @Before
    public void setUp() {
        when(bluetoothManager.getDeviceGovernor(TINYB_DEVICE_URL)).thenReturn(tinybGovernor);
        when(bluetoothManager.getDeviceGovernor(BLUEGIGA_DEVICE_URL)).thenReturn(bluegigaGovernor);

        when(tinybDiscoveredAdapter.getURL()).thenReturn(TINYB_ADAPTER_URL);
        when(bluegigaDiscoveredAdapter.getURL()).thenReturn(BLUEGIGA_ADAPTER_URL);
        when(bluetoothManager.getDiscoveredAdapters()).thenReturn(
                Stream.of(tinybDiscoveredAdapter, bluegigaDiscoveredAdapter).collect(Collectors.toSet()));

        when(tinybGovernor.getBluetoothClass()).thenReturn(BLUETOOTH_CLASS);
        when(bluegigaGovernor.getBluetoothClass()).thenReturn(BLUETOOTH_CLASS);
        when(tinybGovernor.isBleEnabled()).thenReturn(BLE_ENABLED);
        when(bluegigaGovernor.isBleEnabled()).thenReturn(BLE_ENABLED);
        when(tinybGovernor.getName()).thenReturn("TinyB Device");
        when(bluegigaGovernor.getName()).thenReturn(BLUEGIGA_DEVICE_NAME);

        when(tinybGovernor.getRSSI()).thenReturn(TINYB_RSSI);
        when(tinybGovernor.getEstimatedDistance()).thenReturn(TINYB_ESTIMATED_DISTANCE);
        when(bluegigaGovernor.getRSSI()).thenReturn(BLUEGIGA_RSSI);
        when(bluegigaGovernor.getEstimatedDistance()).thenReturn(BLUEGIGA_ESTIMATED_DISTANCE);

        when(tinybGovernor.getLastInteracted()).thenReturn(Instant.now());
        when(bluegigaGovernor.getLastInteracted()).thenReturn(Instant.now());

        when(tinybGovernor.isOnline()).thenReturn(true);
        when(bluegigaGovernor.isOnline()).thenReturn(true);

        when(tinybGovernor.isConnected()).thenReturn(false);
        when(tinybGovernor.isServicesResolved()).thenReturn(false);
        when(bluegigaGovernor.isConnected()).thenReturn(true);
        when(bluegigaGovernor.isServicesResolved()).thenReturn(true);
        when(bluegigaGovernor.getResolvedServices()).thenReturn(mock(List.class));

        MockUtils.mockImplicitNotifications(bluetoothManager);

        governor.setConnectionControl(CONNECTION_CONTROL);
        governor.setAlias(DEVICE_ALIAS);
        governor.setBlockedControl(BLOCKED_CONTROL);
        governor.setMeasuredTxPower(MEASURED_TX_POWER);
        governor.setOnlineTimeout(ONLINE_TIMEOUT);
        governor.setRssiFilteringEnabled(RSSI_FILTERING_ENABLED);
        governor.setRssiReportingRate(RSSI_REPORTING_RATE);
        governor.setSignalPropagationExponent(SIGNAL_PROPAGATION_EXPONENT);

        governor.addGovernorListener(governorListener);
        governor.addBluetoothSmartDeviceListener(bluetoothSmartDeviceListener);
        governor.addGenericBluetoothDeviceListener(genericBluetoothDeviceListener);
    }

    @Test
    public void testInit() {
        // firstly both delegates are not ready
        when(tinybGovernor.isReady()).thenReturn(false);
        when(bluegigaGovernor.isReady()).thenReturn(false);

        // capturing listeners so we can check if they are getting installed
        doNothing().when(tinybGovernor).addGovernorListener(tinybGovernorListenerDelegateCaptor.capture());
        doNothing().when(bluegigaGovernor).addGovernorListener(bluegigaGovernorListenerDelegateCaptor.capture());
        doNothing().when(tinybGovernor).addBluetoothSmartDeviceListener(tinybBluetoothSmartListenerDelegateCaptor.capture());
        doNothing().when(bluegigaGovernor).addBluetoothSmartDeviceListener(bluegigaBluetoothSmartListenerDelegateCaptor.capture());
        doNothing().when(tinybGovernor).addGenericBluetoothDeviceListener(tinybGenericBluetoothListenerDelegateCaptor.capture());
        doNothing().when(bluegigaGovernor).addGenericBluetoothDeviceListener(bluegigaGenericBluetoothListenerDelegateCaptor.capture());

        // performing the invocation to be tested
        governor.init();

        // check if corresponding listeners are installed into each delegate
        verify(tinybGovernor).addGovernorListener(tinybGovernorListenerDelegateCaptor.getValue());
        verify(bluegigaGovernor).addGovernorListener(bluegigaGovernorListenerDelegateCaptor.getValue());
        verify(tinybGovernor).addBluetoothSmartDeviceListener(tinybBluetoothSmartListenerDelegateCaptor.getValue());
        verify(bluegigaGovernor).addBluetoothSmartDeviceListener(bluegigaBluetoothSmartListenerDelegateCaptor.getValue());
        verify(tinybGovernor).addGenericBluetoothDeviceListener(tinybGenericBluetoothListenerDelegateCaptor.getValue());
        verify(bluegigaGovernor).addGenericBluetoothDeviceListener(bluegigaGenericBluetoothListenerDelegateCaptor.getValue());
        assertEquals(1, tinybGovernorListenerDelegateCaptor.getAllValues().size());
        assertEquals(1, bluegigaGovernorListenerDelegateCaptor.getAllValues().size());

        // check if basic/safe attributes are set
        assertEquals(0, governor.getBluetoothClass());
        assertFalse(governor.isBleEnabled());
        assertEquals(URL.getDeviceAddress(), governor.getName());
        assertEquals(0, governor.getRSSI());
        assertFalse(governor.isBlocked());
        assertFalse(governor.isConnected());
        assertFalse(governor.isServicesResolved());
        verifySafe(tinybGovernor);
        verifySafe(bluegigaGovernor);

        // emulate all of the delegates become ready
        when(tinybGovernor.isReady()).thenReturn(true);
        when(bluegigaGovernor.isReady()).thenReturn(true);
        tinybGovernorListenerDelegateCaptor.getValue().ready(true);
        bluegigaGovernorListenerDelegateCaptor.getValue().ready(true);

        // check if "unsafe" attributes are set
        assertEquals(BLUETOOTH_CLASS, governor.getBluetoothClass());
        assertTrue(governor.isBleEnabled());
        assertEquals(BLUEGIGA_DEVICE_NAME, governor.getName());
        assertEquals(BLUEGIGA_RSSI, governor.getRSSI());
        assertFalse(governor.isBlocked());
        assertTrue(governor.isConnected());
        assertTrue(governor.isServicesResolved());
        assertNotNull(governor.getResolvedServices());
        verifyUnsafe(tinybGovernor);
        verifyUnsafe(bluegigaGovernor);

        // check if listeners are triggered
        verify(governorListener).ready(true);
        verify(governorListener, atLeastOnce()).lastUpdatedChanged(any());

        verify(bluetoothSmartDeviceListener).connected();
        verify(bluetoothSmartDeviceListener).servicesResolved(any());

        verify(genericBluetoothDeviceListener).online();
        verify(genericBluetoothDeviceListener).rssiChanged(TINYB_RSSI);
        verify(genericBluetoothDeviceListener, never()).blocked(false);
    }

    @Test
    public void testDisconnected() {
        when(tinybGovernor.isReady()).thenReturn(true);
        when(bluegigaGovernor.isReady()).thenReturn(true);

        doNothing().when(tinybGovernor).addBluetoothSmartDeviceListener(tinybBluetoothSmartListenerDelegateCaptor.capture());
        doNothing().when(bluegigaGovernor).addBluetoothSmartDeviceListener(bluegigaBluetoothSmartListenerDelegateCaptor.capture());

        governor.init();
        assertTrue(governor.isReady());

        tinybBluetoothSmartListenerDelegateCaptor.getValue().connected();
        assertTrue(governor.isConnected());
        verify(bluetoothSmartDeviceListener).connected();

        tinybBluetoothSmartListenerDelegateCaptor.getValue().disconnected();
        assertFalse(governor.isConnected());
        verify(bluetoothSmartDeviceListener).disconnected();
    }

    private static void verifyUnsafe(DeviceGovernor deviceGovernor) {
        verify(deviceGovernor).setAlias(DEVICE_ALIAS);

        verify(deviceGovernor).getBluetoothClass();
        verify(deviceGovernor).isBleEnabled();
        verify(deviceGovernor).getName();
        verify(deviceGovernor).getRSSI();
        verify(deviceGovernor).isBlocked();
        verify(deviceGovernor).isConnected();
        verify(deviceGovernor).isServicesResolved();
    }

    private static void verifySafe(DeviceGovernor deviceGovernor) {
        verify(deviceGovernor).setConnectionControl(false);
        verify(deviceGovernor).setBlockedControl(BLOCKED_CONTROL);
        verify(deviceGovernor).setOnlineTimeout(ONLINE_TIMEOUT);
        verify(deviceGovernor).setRssiFilteringEnabled(RSSI_FILTERING_ENABLED);
        verify(deviceGovernor).setRssiFilter(RssiKalmanFilter.class);
        // must be set to 0 to allow proper calculation of the nearest adapter
        verify(deviceGovernor).setRssiReportingRate(0);
        verify(deviceGovernor).setSignalPropagationExponent(SIGNAL_PROPAGATION_EXPONENT);

        verify(deviceGovernor, never()).setAlias(any());
        verify(deviceGovernor, never()).getAlias();
        verify(deviceGovernor, never()).getBluetoothClass();
        verify(deviceGovernor, never()).isBleEnabled();
        verify(deviceGovernor, never()).getName();
        verify(deviceGovernor, never()).getRSSI();
        verify(deviceGovernor, never()).isBlocked();
        verify(deviceGovernor, never()).isConnected();
        verify(deviceGovernor, never()).isServicesResolved();

    }

}