package org.sputnikdev.bluetooth.manager.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.internal.util.reflection.Whitebox;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.*;
import org.sputnikdev.bluetooth.manager.transport.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(BluetoothObjectFactoryProvider.class)
public class DeviceGovernorImplTest {

    private static final int BLUETOOTH_CLASS = 0;
    private static final String ALIAS = "device alias";
    private static final String NAME = "device name";
    private static final short RSSI = -90;

    @Mock
    private static final URL URL = new URL("/11:22:33:44:55:66/12:34:56:78:90:12");
    private static final String SERVICE_1 = "0000180f-0000-1000-8000-00805f9b34fb";
    private static final URL SERVICE_1_URL = URL.copyWith(SERVICE_1, null);
    private static final URL CHARACTERISTIC_1_URL = URL.copyWith(SERVICE_1, "00002a19-0000-1000-8000-00805f9b34fb");
    private static final URL CHARACTERISTIC_2_URL = URL.copyWith(SERVICE_1, "00002a20-0000-1000-8000-00805f9b34fb");

    @Mock(name = "bluetoothObject")
    private Device device;
    @Mock
    private BluetoothManagerImpl bluetoothManager = mock(BluetoothManagerImpl.class);
    @Mock
    private GenericBluetoothDeviceListener genericDeviceListener;
    @Mock
    private BluetoothSmartDeviceListener bluetoothSmartDeviceListener;
    @Mock
    private BluetoothObjectFactory bluetoothObjectFactory;

    @Spy
    @InjectMocks
    private DeviceGovernorImpl governor = new DeviceGovernorImpl(bluetoothManager, URL);

    @Captor
    private ArgumentCaptor<Notification<Short>> rssiCaptor;
    @Captor
    private ArgumentCaptor<Notification<Boolean>> blockedCaptor;
    @Captor
    private ArgumentCaptor<Notification<Boolean>> connectedCaptor;
    @Captor
    private ArgumentCaptor<Notification<Boolean>> servicesResolvedCaptor;

    @Before
    public void setUp() throws Exception {
        // not sure why, but adapter does not get injected properly, hence a workaround here:
        Whitebox.setInternalState(governor, "bluetoothObject", device);

        doNothing().when(device).enableRSSINotifications(rssiCaptor.capture());
        doNothing().when(device).enableBlockedNotifications(blockedCaptor.capture());
        doNothing().when(device).enableConnectedNotifications(connectedCaptor.capture());
        doNothing().when(device).enableServicesResolvedNotifications(servicesResolvedCaptor.capture());

        governor.addGenericBluetoothDeviceListener(genericDeviceListener);
        governor.addBluetoothSmartDeviceListener(bluetoothSmartDeviceListener);

        PowerMockito.mockStatic(BluetoothObjectFactoryProvider.class);
        when(BluetoothObjectFactoryProvider.getFactory(any())).thenReturn(bluetoothObjectFactory);
        when(bluetoothObjectFactory.getDevice(URL)).thenReturn(device);

        when(device.getBluetoothClass()).thenReturn(BLUETOOTH_CLASS);
        when(device.getAlias()).thenReturn(ALIAS);
        when(device.getName()).thenReturn(NAME);

        List<Service> services = new ArrayList<>();
        List<Characteristic> characteristics = new ArrayList<>();


        Service gattService = mock(Service.class);
        when(gattService.getURL()).thenReturn(SERVICE_1_URL);
        services.add(gattService);

        Characteristic gattCharacteristic = mock(Characteristic.class);
        characteristics.add(gattCharacteristic);
        when(gattCharacteristic.getURL()).thenReturn(CHARACTERISTIC_1_URL);


        gattCharacteristic = mock(Characteristic.class);
        characteristics.add(gattCharacteristic);
        when(gattCharacteristic.getURL()).thenReturn(CHARACTERISTIC_2_URL);

        when(gattService.getCharacteristics()).thenReturn(characteristics);
        when(device.getServices()).thenReturn(services);

        List<BluetoothGovernor> charGovernors = new ArrayList<>();
        charGovernors.add(mockCharacteristicGovernor(CHARACTERISTIC_1_URL));
        charGovernors.add(mockCharacteristicGovernor(CHARACTERISTIC_2_URL));
        when(bluetoothManager.getGovernors(any())).thenReturn(charGovernors);

        AdapterGovernorImpl adapterGovernor = mock(AdapterGovernorImpl.class);
        when(adapterGovernor.isReady()).thenReturn(true);
        when(adapterGovernor.isPowered()).thenReturn(true);
        when(bluetoothManager.getAdapterGovernor(URL)).thenReturn(adapterGovernor);
    }

    @Test
    public void testInit() throws Exception {
        governor.init(device);

        verify(device, times(1)).enableRSSINotifications(rssiCaptor.getValue());
        verify(device, times(1)).enableBlockedNotifications(blockedCaptor.getValue());
        verify(device, times(1)).enableConnectedNotifications(connectedCaptor.getValue());
        verify(device, times(1)).enableServicesResolvedNotifications(servicesResolvedCaptor.getValue());

        verifyNoMoreInteractions(device, genericDeviceListener, bluetoothSmartDeviceListener);
    }

    @Test
    public void testUpdateBlocked() throws Exception {
        governor.setBlockedControl(false);
        when(device.isBlocked()).thenReturn(false);
        governor.update(device);
        verify(device, never()).setBlocked(false);

        governor.setBlockedControl(false);
        when(device.isBlocked()).thenReturn(true);
        governor.update(device);
        verify(device, times(1)).setBlocked(false);

        governor.setBlockedControl(true);
        when(device.isBlocked()).thenReturn(true);
        governor.update(device);
        verify(device, never()).setBlocked(true);

        governor.setBlockedControl(true);
        when(device.isBlocked()).thenReturn(false);
        governor.update(device);
        verify(device, times(1)).setBlocked(true);
    }

    @Test
    public void testUpdateConnectedBleDevice() throws Exception {
        doReturn(true).when(governor).isBleEnabled();

        short rssi = -77;

        governor.setBlockedControl(false);
        when(device.isBlocked()).thenReturn(false);
        when(device.isConnected()).thenReturn(false).thenReturn(true).thenReturn(false).thenReturn(true);
        when(device.connect()).thenReturn(true);
        when(device.getRSSI()).thenReturn(rssi);

        governor.setConnectionControl(false);

        Date lastChanged = governor.getLastActivity();

        governor.update(device);
        verify(device, never()).connect();
        verify(device, never()).disconnect();

        governor.update(device);
        verify(device, never()).connect();
        verify(device, times(1)).disconnect();
        verify(bluetoothManager, times(1)).resetDescendants(URL);
        verify(device, never()).getRSSI();
        verify(genericDeviceListener, never()).rssiChanged(rssi);
        assertEquals(lastChanged, governor.getLastActivity());

        governor.setConnectionControl(true);

        governor.update(device);
        verify(device, times(1)).connect();
        verify(device, times(1)).disconnect();
        verify(device, times(1)).getRSSI();
        verify(genericDeviceListener).rssiChanged(rssi);
        assertTrue(lastChanged.before(governor.getLastActivity()));
        lastChanged = governor.getLastActivity();

        rssi = -87;
        when(device.getRSSI()).thenReturn(rssi);

        governor.update(device);
        verify(device, times(1)).connect();
        verify(device, times(1)).disconnect();
        verify(genericDeviceListener).rssiChanged(rssi);
        assertTrue(lastChanged.before(governor.getLastActivity()));

        verify(device, atLeastOnce()).isBlocked();
        verify(device, atLeastOnce()).isConnected();
    }

    @Test
    public void testUpdateAdapterIsNotPowered() throws Exception {
        AdapterGovernorImpl adapterGovernor = mock(AdapterGovernorImpl.class);
        when(adapterGovernor.isReady()).thenReturn(false);
        when(adapterGovernor.isPowered()).thenReturn(false);
        when(bluetoothManager.getAdapterGovernor(URL)).thenReturn(adapterGovernor);

        governor.update(device);
        verifyNoMoreInteractions(device);
    }

    @Test
    public void testUpdateOnline() throws Exception {
        int onlineTimeout = 20;

        governor.setBlockedControl(false);
        governor.setConnectionControl(true);
        when(device.isBlocked()).thenReturn(false);
        when(device.isConnected()).thenReturn(true);
        when(device.connect()).thenReturn(true);
        governor.setOnlineTimeout(onlineTimeout);

        governor.update(device);
        verify(genericDeviceListener, times(1)).online();

        governor.update(device);
        verify(genericDeviceListener, times(1)).online();

        governor.setConnectionControl(false);

        governor.update(device);
        verify(genericDeviceListener, times(0)).offline();

        Whitebox.setInternalState(governor, "lastActivity", Date.from(Instant.now().minusSeconds(onlineTimeout)));

        governor.update(device);
        verify(genericDeviceListener, times(1)).offline();
    }

    @Test
    public void testReset() throws Exception {
        Whitebox.setInternalState(governor, "online", false);
        when(device.isConnected()).thenReturn(false);

        governor.reset(device);

        verify(device, times(1)).disableRSSINotifications();
        verify(device, times(1)).disableServicesResolvedNotifications();
        verify(device, times(1)).disableConnectedNotifications();
        verify(device, times(1)).disableBlockedNotifications();
        verify(device, times(1)).isConnected();
        verify(device, times(0)).disconnect();
        verify(bluetoothSmartDeviceListener, times(0)).disconnected();
        verify(genericDeviceListener, times(0)).offline();
        verify(bluetoothManager, times(0)).resetDescendants(URL);

        Whitebox.setInternalState(governor, "online", true);
        when(device.isConnected()).thenReturn(true);

        governor.reset(device);

        verify(device, times(2)).disableRSSINotifications();
        verify(device, times(2)).disableServicesResolvedNotifications();
        verify(device, times(2)).disableConnectedNotifications();
        verify(device, times(2)).disableBlockedNotifications();
        verify(device, times(2)).isConnected();
        verify(device, times(1)).disconnect();
        verify(bluetoothSmartDeviceListener, times(1)).disconnected();
        verify(genericDeviceListener, times(1)).offline();
        verify(bluetoothManager, times(1)).resetDescendants(URL);

        verifyNoMoreInteractions(device, genericDeviceListener, bluetoothSmartDeviceListener);
    }

    @Test
    public void testGetBluetoothClass() throws Exception {
        assertEquals(BLUETOOTH_CLASS, governor.getBluetoothClass());
        verify(device, times(1)).getBluetoothClass();
    }

    @Test
    public void testIsBleEnabled() throws Exception {
        when(device.isBleEnabled()).thenReturn(true).thenReturn(false);
        assertTrue(governor.isBleEnabled());
        verify(device, times(1)).isBleEnabled();
        assertFalse(governor.isBleEnabled());
        verify(device, times(2)).isBleEnabled();
    }

    @Test
    public void testGetName() throws Exception {
        assertEquals(NAME, governor.getName());

        verify(governor, times(1)).getBluetoothObject();
        verify(device, times(1)).getName();

        verifyNoMoreInteractions(device);
    }

    @Test
    public void testGetDisplayName() throws Exception {
        when(device.getAlias()).thenReturn(ALIAS).thenReturn(null);
        when(device.getName()).thenReturn(NAME);

        assertEquals(ALIAS, governor.getDisplayName());
        verify(governor, times(1)).getBluetoothObject();
        verify(device, times(1)).getAlias();

        assertEquals(NAME, governor.getDisplayName());
        verify(governor, times(3)).getBluetoothObject();
        verify(device, times(2)).getAlias();
        verify(device, times(1)).getName();

        verifyNoMoreInteractions(device);
    }

    @Test
    public void testGetAlias() throws Exception {
        assertEquals(ALIAS, governor.getAlias());

        verify(governor, times(1)).getBluetoothObject();
        verify(device, times(1)).getAlias();

        verifyNoMoreInteractions(device);
    }

    @Test
    public void testSetAlias() throws Exception {
        String newAlias = "new alias";

        governor.setAlias(newAlias);

        verify(governor, times(1)).getBluetoothObject();
        verify(device, times(1)).setAlias(newAlias);

        verifyNoMoreInteractions(device);
    }

    @Test
    public void testGetSetConnectionControl() throws Exception {
        governor.setConnectionControl(true);
        assertTrue(governor.getConnectionControl());

        governor.setConnectionControl(false);
        assertFalse(governor.getConnectionControl());
    }

    @Test
    public void testGetSetBlockedControl() throws Exception {
        governor.setBlockedControl(true);
        assertTrue(governor.getBlockedControl());

        governor.setBlockedControl(false);
        assertFalse(governor.getBlockedControl());
    }

    @Test
    public void testIsConnected() throws Exception {
        when(device.isConnected()).thenReturn(false).thenReturn(true);

        assertFalse(governor.isConnected());
        verify(device, times(1)).isConnected();
        verify(governor, times(1)).getBluetoothObject();

        assertTrue(governor.isConnected());
        verify(device, times(2)).isConnected();

        verifyNoMoreInteractions(device);
    }

    @Test 
    public void testIsBlocked() throws Exception {
        when(device.isBlocked()).thenReturn(false).thenReturn(true);

        assertFalse(governor.isBlocked());
        verify(device, times(1)).isBlocked();
        verify(governor, times(1)).getBluetoothObject();

        assertTrue(governor.isBlocked());
        verify(device, times(2)).isBlocked();

        verifyNoMoreInteractions(device);   
    }

    @Test
    public void testIsOnline() throws Exception {
        int onlineTimeout = 20;
        governor.setOnlineTimeout(onlineTimeout);

        Whitebox.setInternalState(governor, "lastActivity", Date.from(Instant.now()));
        assertTrue(governor.isOnline());

        Whitebox.setInternalState(governor, "lastActivity", Date.from(Instant.now().minusSeconds(onlineTimeout)));
        assertFalse(governor.isOnline());
    }

    @Test
    public void testGetRSSI() throws Exception {
        when(device.getRSSI()).thenReturn(RSSI);

        assertEquals(RSSI, governor.getRSSI());
        verify(device, times(1)).getRSSI();
    }

    @Test
    public void testAddRemoveBluetoothSmartDeviceListener() throws Exception {
        BluetoothSmartDeviceListener listener = mock(BluetoothSmartDeviceListener.class);
        governor.addBluetoothSmartDeviceListener(listener);
        ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
        doNothing().when(listener).servicesResolved(listArgumentCaptor.capture());

        governor.notifyConnected(false);
        governor.notifyServicesResolved(true);
        verify(listener, never()).connected();
        verify(listener, times(1)).disconnected();
        verify(listener, never()).servicesUnresolved();
        verify(listener, times(1)).servicesResolved(listArgumentCaptor.getValue());

        governor.notifyConnected(true);
        governor.notifyServicesResolved(false);
        verify(listener, times(1)).connected();
        verify(listener, times(1)).disconnected();
        verify(listener, times(1)).servicesUnresolved();
        verify(listener, times(1)).servicesResolved(listArgumentCaptor.getValue());

        List<GattService> gattServices = listArgumentCaptor.getValue();
        assertEquals(1, gattServices.size());
        GattService gattService = gattServices.get(0);
        assertEquals(SERVICE_1_URL, gattService.getURL());
        assertEquals(2, gattService.getCharacteristics().size());
        List<GattCharacteristic> gattCharacteristics = new ArrayList(gattService.getCharacteristics());
        Collections.sort(gattCharacteristics, new Comparator<GattCharacteristic>() {
            @Override public int compare(GattCharacteristic first, GattCharacteristic second) {
                return first.getURL().compareTo(second.getURL());
            }
        });
        assertEquals(CHARACTERISTIC_1_URL, gattCharacteristics.get(0).getURL());
        assertEquals(CHARACTERISTIC_2_URL, gattCharacteristics.get(1).getURL());

        governor.removeBluetoothSmartDeviceListener(listener);

        governor.notifyConnected(true);
        governor.notifyServicesResolved(true);
        verifyNoMoreInteractions(listener);
    }

    @Test
    public void testAddRemoveGenericBluetoothDeviceListener() throws Exception {
        GenericBluetoothDeviceListener listener = mock(GenericBluetoothDeviceListener.class);
        governor.addGenericBluetoothDeviceListener(listener);

        governor.notifyBlocked(true);
        governor.notifyRSSIChanged((short) -45);
        governor.notifyOnline(false);
        verify(listener, times(1)).blocked(true);
        verify(listener, times(1)).rssiChanged((short) -45);
        verify(listener, times(1)).offline();
        verify(listener, never()).online();

        governor.notifyBlocked(false);
        governor.notifyRSSIChanged((short) -84);
        governor.notifyOnline(true);
        verify(listener, times(1)).blocked(false);
        verify(listener, times(1)).rssiChanged((short) -84);
        verify(listener, times(1)).offline();
        verify(listener, times(1)).online();

        governor.removeGenericBluetoothDeviceListener(listener);
        governor.notifyBlocked(true);
        governor.notifyRSSIChanged((short) -64);
        governor.notifyOnline(false);
        verifyNoMoreInteractions(listener);
    }


    @Test
    public void testGetServicesToCharacteristicsMap() throws Exception {
        Map<URL, List<CharacteristicGovernor>> characteristicsMap = governor.getServicesToCharacteristicsMap();
        assertEquals(1, characteristicsMap.size());
        assertEquals(SERVICE_1_URL, characteristicsMap.keySet().iterator().next());
        assertEquals(2, characteristicsMap.get(SERVICE_1_URL).size());
        List<CharacteristicGovernor> gattCharacteristics = new ArrayList(characteristicsMap.get(SERVICE_1_URL));
        Collections.sort(gattCharacteristics, new Comparator<CharacteristicGovernor>() {
            @Override public int compare(CharacteristicGovernor first, CharacteristicGovernor second) {
                return first.getURL().compareTo(second.getURL());
            }
        });
        assertEquals(CHARACTERISTIC_1_URL, gattCharacteristics.get(0).getURL());
        assertEquals(CHARACTERISTIC_2_URL, gattCharacteristics.get(1).getURL());
    }

    @Test
    public void testGetCharacteristics() throws Exception {
        List<URL> characteristics = new ArrayList(governor.getCharacteristics());
        assertEquals(2, characteristics.size());
        Collections.sort(characteristics);
        assertEquals(CHARACTERISTIC_1_URL, characteristics.get(0));
        assertEquals(CHARACTERISTIC_2_URL, characteristics.get(1));
    }

    @Test
    public void testGetCharacteristicGovernors() throws Exception {
        List<CharacteristicGovernor> governors = new ArrayList(governor.getCharacteristicGovernors());
        assertEquals(2, governors.size());
        Collections.sort(governors, new Comparator<CharacteristicGovernor>() {
            @Override public int compare(CharacteristicGovernor first, CharacteristicGovernor second) {
                return first.getURL().compareTo(second.getURL());
            }
        });
        assertEquals(CHARACTERISTIC_1_URL, governors.get(0).getURL());
        assertEquals(CHARACTERISTIC_2_URL, governors.get(1).getURL());
    }

    @Test
    public void testToString() throws Exception {
        when(device.getAlias()).thenReturn(ALIAS).thenReturn(null);
        when(device.isBleEnabled()).thenReturn(true).thenReturn(false);
        assertEquals("[Device] " + URL + " [" + ALIAS + "] [BLE]", governor.toString());
        assertEquals("[Device] " + URL + " [" + NAME + "]", governor.toString());
    }

    @Test
    public void testEquals() throws Exception {
        URL url1 = new URL("/11:22:33:44:55:67/12:34:56:78:90:12");
        URL url2 = new URL("/11:22:33:44:55:66/12:34:56:78:90:13");

        assertFalse(url1.equals(url2));

        DeviceGovernorImpl gov1 = new DeviceGovernorImpl(bluetoothManager, url1);
        DeviceGovernorImpl gov2 = new DeviceGovernorImpl(bluetoothManager, url2);

        assertEquals(gov1, gov1);
        assertFalse(gov1.equals(new Object()));

        assertFalse(gov1.equals(gov2));

        gov2 = new DeviceGovernorImpl(bluetoothManager, url1);
        assertTrue(gov1.equals(gov2));
    }

    @Test
    public void testHashCode() throws Exception {
        URL url1 = new URL("/11:22:33:44:55:67/12:34:56:78:90:12");
        URL url2 = new URL("/11:22:33:44:55:66/12:34:56:78:90:13");

        assertFalse(url1.hashCode() == url2.hashCode());

        DeviceGovernorImpl gov1 = new DeviceGovernorImpl(bluetoothManager, url1);
        DeviceGovernorImpl gov2 = new DeviceGovernorImpl(bluetoothManager, url2);
        assertFalse(gov1.hashCode() == gov2.hashCode());

        gov2 = new DeviceGovernorImpl(bluetoothManager, url1);
        assertTrue(gov1.hashCode() == gov2.hashCode());
    }

    @Test
    public void testGetType() throws Exception {
        assertEquals(BluetoothObjectType.DEVICE, governor.getType());
    }

    @Test
    public void testAccept() throws Exception {
        governor.accept(new BluetoothObjectVisitor() {
            @Override public void visit(AdapterGovernor governor) throws Exception {
                assertFalse(true);
            }

            @Override public void visit(DeviceGovernor governor) throws Exception {
                assertEquals(DeviceGovernorImplTest.this.governor, governor);
            }

            @Override public void visit(CharacteristicGovernor governor) throws Exception {
                assertFalse(true);
            }
        });
    }

    @Test
    public void testGetSetOnlineTimeout() {
        governor.setOnlineTimeout(50);
        assertEquals(50, governor.getOnlineTimeout());
    }

    @Test
    public void testNotifyOnline() {
        GenericBluetoothDeviceListener listener1 = mock(GenericBluetoothDeviceListener.class);
        GenericBluetoothDeviceListener listener2 = mock(GenericBluetoothDeviceListener.class);
        governor.addGenericBluetoothDeviceListener(listener1);
        governor.addGenericBluetoothDeviceListener(listener2);

        governor.notifyOnline(true);

        InOrder inOrder = inOrder(listener1, listener2);

        inOrder.verify(listener1, times(1)).online();
        inOrder.verify(listener2, times(1)).online();

        // this should be ignored by governor, a log message must be issued
        doThrow(Exception.class).when(listener1).online();

        governor.notifyOnline(true);
        inOrder.verify(listener1, times(1)).online();
        inOrder.verify(listener2, times(1)).online();

        inOrder.verify(listener1, never()).offline();
        inOrder.verify(listener2, never()).offline();

        governor.notifyOnline(false);
        inOrder.verify(listener1, times(1)).offline();
        inOrder.verify(listener2, times(1)).offline();

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testNotifyRSSIChanged() {
        GenericBluetoothDeviceListener listener1 = mock(GenericBluetoothDeviceListener.class);
        GenericBluetoothDeviceListener listener2 = mock(GenericBluetoothDeviceListener.class);
        governor.addGenericBluetoothDeviceListener(listener1);
        governor.addGenericBluetoothDeviceListener(listener2);

        governor.notifyRSSIChanged(RSSI);

        InOrder inOrder = inOrder(listener1, listener2);

        inOrder.verify(listener1, times(1)).rssiChanged(RSSI);
        inOrder.verify(listener2, times(1)).rssiChanged(RSSI);

        // this should be ignored by governor, a log message must be issued
        doThrow(Exception.class).when(listener1).rssiChanged(RSSI);

        governor.notifyRSSIChanged(RSSI);
        inOrder.verify(listener1, times(1)).rssiChanged(RSSI);
        inOrder.verify(listener2, times(1)).rssiChanged(RSSI);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testNotifyServicesResolved() {
        BluetoothSmartDeviceListener listener1 = mock(BluetoothSmartDeviceListener.class);
        BluetoothSmartDeviceListener listener2 = mock(BluetoothSmartDeviceListener.class);
        governor.addBluetoothSmartDeviceListener(listener1);
        governor.addBluetoothSmartDeviceListener(listener2);

        governor.notifyServicesResolved(true);

        InOrder inOrder = inOrder(listener1, listener2);

        inOrder.verify(listener1, times(1)).servicesResolved(any());
        inOrder.verify(listener2, times(1)).servicesResolved(any());

        // this should be ignored by governor, a log message must be issued
        doThrow(Exception.class).when(listener1).servicesResolved(any());

        governor.notifyServicesResolved(true);
        inOrder.verify(listener1, times(1)).servicesResolved(any());
        inOrder.verify(listener2, times(1)).servicesResolved(any());

        inOrder.verify(listener1, never()).servicesUnresolved();
        inOrder.verify(listener2, never()).servicesUnresolved();

        governor.notifyServicesResolved(false);
        inOrder.verify(listener1, times(1)).servicesUnresolved();
        inOrder.verify(listener2, times(1)).servicesUnresolved();

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testNotifyConnected() {
        BluetoothSmartDeviceListener listener1 = mock(BluetoothSmartDeviceListener.class);
        BluetoothSmartDeviceListener listener2 = mock(BluetoothSmartDeviceListener.class);
        governor.addBluetoothSmartDeviceListener(listener1);
        governor.addBluetoothSmartDeviceListener(listener2);

        governor.notifyConnected(true);

        InOrder inOrder = inOrder(listener1, listener2);

        inOrder.verify(listener1, times(1)).connected();
        inOrder.verify(listener2, times(1)).connected();

        // this should be ignored by governor, a log message must be issued
        doThrow(Exception.class).when(listener1).connected();

        governor.notifyConnected(true);
        inOrder.verify(listener1, times(1)).connected();
        inOrder.verify(listener2, times(1)).connected();

        inOrder.verify(listener1, never()).disconnected();
        inOrder.verify(listener2, never()).disconnected();

        governor.notifyConnected(false);
        inOrder.verify(listener1, times(1)).disconnected();
        inOrder.verify(listener2, times(1)).disconnected();

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testNotifyBlocked() throws Exception {
        GenericBluetoothDeviceListener listener1 = mock(GenericBluetoothDeviceListener.class);
        GenericBluetoothDeviceListener listener2 = mock(GenericBluetoothDeviceListener.class);
        governor.addGenericBluetoothDeviceListener(listener1);
        governor.addGenericBluetoothDeviceListener(listener2);

        governor.notifyBlocked(true);

        InOrder inOrder = inOrder(listener1, listener2);

        inOrder.verify(listener1, times(1)).blocked(true);
        inOrder.verify(listener2, times(1)).blocked(true);

        // this should be ignored by governor, a log message must be issued
        doThrow(Exception.class).when(listener1).blocked(anyBoolean());

        governor.notifyBlocked(true);
        inOrder.verify(listener1, times(1)).blocked(true);
        inOrder.verify(listener2, times(1)).blocked(true);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testConnectionNotification() {
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);

        doNothing().when(device).enableConnectedNotifications(notificationCaptor.capture());

        // init method will enable notifications, if they are not enabled already
        governor.init(device);

        verify(device, times(1)).enableConnectedNotifications(notificationCaptor.getValue());

        notificationCaptor.getValue().notify(Boolean.TRUE);

        verify(bluetoothSmartDeviceListener, times(1)).connected();
        verify(governor, times(1)).notifyConnected(true);
        verify(governor, times(1)).updateLastChanged();

        notificationCaptor.getValue().notify(Boolean.FALSE);
        verify(bluetoothSmartDeviceListener, times(1)).disconnected();
        verify(governor, times(1)).notifyConnected(false);
        verify(governor, times(2)).updateLastChanged();
    }

    @Test
    public void testServicesResolvedNotification() {
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);

        doNothing().when(device).enableServicesResolvedNotifications(notificationCaptor.capture());

        // init method will enable notifications, if they are not enabled already
        governor.init(device);

        verify(device, times(1)).enableServicesResolvedNotifications(notificationCaptor.getValue());

        notificationCaptor.getValue().notify(Boolean.TRUE);

        verify(bluetoothSmartDeviceListener, times(1)).servicesResolved(any());
        verify(governor, times(1)).notifyServicesResolved(true);
        verify(governor, times(1)).updateLastChanged();
        verify(bluetoothManager, times(1)).updateDescendants(URL);

        notificationCaptor.getValue().notify(Boolean.FALSE);
        verify(bluetoothSmartDeviceListener, times(1)).servicesUnresolved();
        verify(governor, times(1)).notifyServicesResolved(false);
        verify(governor, times(2)).updateLastChanged();
        verify(bluetoothManager, times(1)).resetDescendants(URL);
    }

    @Test
    public void testRSSINotification() {
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);

        doNothing().when(device).enableRSSINotifications(notificationCaptor.capture());

        // init method will enable notifications, if they are not enabled already
        governor.init(device);

        verify(device, times(1)).enableRSSINotifications(notificationCaptor.getValue());

        notificationCaptor.getValue().notify(RSSI);

        verify(genericDeviceListener, times(1)).rssiChanged(RSSI);
        verify(governor, times(1)).notifyRSSIChanged(RSSI);
        verify(governor, times(1)).updateLastChanged();
    }

    @Test
    public void testBlockedNotification() {
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);

        doNothing().when(device).enableBlockedNotifications(notificationCaptor.capture());

        // init method will enable notifications, if they are not enabled already
        governor.init(device);

        verify(device, times(1)).enableBlockedNotifications(notificationCaptor.getValue());

        notificationCaptor.getValue().notify(true);

        verify(genericDeviceListener, times(1)).blocked(true);
        verify(governor, times(1)).notifyBlocked(true);
        verify(governor, times(1)).updateLastChanged();

        notificationCaptor.getValue().notify(false);

        verify(genericDeviceListener, times(1)).blocked(false);
        verify(governor, times(1)).notifyBlocked(false);
        verify(governor, times(2)).updateLastChanged();
    }

    private CharacteristicGovernor mockCharacteristicGovernor(URL url) {
        CharacteristicGovernor governor = mock(CharacteristicGovernor.class);
        when(governor.getURL()).thenReturn(url);
        return governor;
    }
}
