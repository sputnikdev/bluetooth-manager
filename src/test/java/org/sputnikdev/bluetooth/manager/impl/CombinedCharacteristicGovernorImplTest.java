package org.sputnikdev.bluetooth.manager.impl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.CharacteristicGovernor;
import org.sputnikdev.bluetooth.manager.DiscoveredAdapter;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.ManagerListener;
import org.sputnikdev.bluetooth.manager.ValueListener;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CombinedCharacteristicGovernorImplTest {

    private static final URL URL = new URL("/XX:XX:XX:XX:XX:XX/12:34:56:78:90:12");
    private static final String SERVICE = "0000180f-0000-1000-8000-00805f9b34fb";
    private static final URL CHARACTERISTIC_URL = URL.copyWith(SERVICE, "00002a19-0000-1000-8000-00805f9b34fb");
    private static final URL ADAPTER_1 = new URL("/11:11:11:11:11:11");
    private static final URL ADAPTER_2 = new URL("/22:22:22:22:22:22");
    private static final URL CHARACTERISTIC_1 = CHARACTERISTIC_URL.copyWithAdapter(ADAPTER_1.getAdapterAddress());
    private static final URL CHARACTERISTIC_2 = CHARACTERISTIC_URL.copyWithAdapter(ADAPTER_2.getAdapterAddress());
    private static final Instant LAST_NOTIFIED = Instant.now().minusSeconds(2);
    private static final Instant LAST_INTERACTED = Instant.now().minusSeconds(1);


    private BluetoothManagerImpl bluetoothManager = mock(BluetoothManagerImpl.class);

    @Mock
    private CharacteristicGovernor delegate1;
    @Mock
    private CharacteristicGovernor delegate2;
    @Mock
    private DiscoveredAdapter adapter1;
    @Mock
    private DiscoveredAdapter adapter2;
    @Mock
    private GovernorListener governorListener;
    @Mock
    private ValueListener valueListener;

    @Captor
    private ArgumentCaptor<ManagerListener> managerListenerArgumentCaptor;

    //@Spy
    private CombinedCharacteristicGovernorImpl governor = new CombinedCharacteristicGovernorImpl(bluetoothManager, CHARACTERISTIC_URL);


    @Before
    public void setUp() {
        when(delegate1.getURL()).thenReturn(CHARACTERISTIC_URL.copyWithAdapter(ADAPTER_1.getAdapterAddress()));
        when(delegate2.getURL()).thenReturn(CHARACTERISTIC_URL.copyWithAdapter(ADAPTER_2.getAdapterAddress()));

        when(delegate2.getLastNotified()).thenReturn(LAST_NOTIFIED);
        when(delegate2.getLastInteracted()).thenReturn(LAST_INTERACTED);

        when(adapter1.getURL()).thenReturn(ADAPTER_1);
        when(adapter2.getURL()).thenReturn(ADAPTER_2);

        when(bluetoothManager.getDiscoveredAdapters()).thenReturn(new HashSet<>(Arrays.asList(adapter1, adapter2)));
        when(bluetoothManager.getCharacteristicGovernor(CHARACTERISTIC_1)).thenReturn(delegate1);
        when(bluetoothManager.getCharacteristicGovernor(CHARACTERISTIC_2)).thenReturn(delegate2);

        governor.addValueListener(valueListener);
        governor.addGovernorListener(governorListener);

        doNothing().when(bluetoothManager).addManagerListener(managerListenerArgumentCaptor.capture());

        doAnswer(answer -> {
            ((Runnable) answer.getArguments()[0]).run();
            return null;
        }).when(bluetoothManager).notify(any(Runnable.class));
    }

    @Test
    public void testInit() {
        CombinedCharacteristicGovernorImpl spy = spy(governor);

        spy.init();

        assertNotNull(managerListenerArgumentCaptor.getValue());
        verify(bluetoothManager).addManagerListener(managerListenerArgumentCaptor.getValue());
        verify(bluetoothManager).getCharacteristicGovernor(CHARACTERISTIC_1);
        verify(bluetoothManager).getCharacteristicGovernor(CHARACTERISTIC_2);
        verify(bluetoothManager).getDiscoveredAdapters();
        verify(delegate1).isReady();
        verify(delegate2).isReady();
        verify(spy).update();

        verifyNoMoreInteractions(bluetoothManager, delegate1, delegate2, governorListener, valueListener);
    }

    @Test
    public void testInitDelegatesNotReady() {
        CombinedCharacteristicGovernorImpl spy = spy(governor);

        CompletableFuture<Boolean> ready = governor.whenReady(gov -> {
            fail();
            return null;
        });

        spy.init();

        assertFalse(ready.isDone());

        verify(spy).update();
        verify(delegate1).isReady();
        verify(delegate2).isReady();
        verify(bluetoothManager).addManagerListener(managerListenerArgumentCaptor.getValue());
        verify(bluetoothManager).getCharacteristicGovernor(CHARACTERISTIC_1);
        verify(bluetoothManager).getCharacteristicGovernor(CHARACTERISTIC_2);
        verify(bluetoothManager).getDiscoveredAdapters();

        verifyNoMoreInteractions(bluetoothManager, delegate1, delegate2, governorListener, valueListener);
    }

    @Test
    public void testInitDelegatesReady() throws ExecutionException, InterruptedException {
        CombinedCharacteristicGovernorImpl spy = spy(governor);
        when(delegate2.isReady()).thenReturn(true);

        CompletableFuture<Boolean> ready = spy.whenReady(gov -> true);

        assertFalse(ready.isDone());
        assertFalse(spy.isReady());

        spy.init();

        verify(spy).update();
        // 2 times: while installing delegate, while completing future
        verify(delegate2, atLeastOnce()).isReady();

        assertDelegateInstallation(delegate2);

        assertTrue(spy.isReady());
        assertEquals(LAST_INTERACTED, spy.getLastInteracted());
        assertEquals(LAST_NOTIFIED, spy.getLastNotified());
        assertTrue(ready.get());
        assertTrue(ready.isDone());

        verify(delegate2, atLeastOnce()).getLastNotified();
        verify(delegate2, atLeastOnce()).getLastInteracted();
        verify(delegate2, atLeastOnce()).isReady();

        verify(bluetoothManager).addManagerListener(managerListenerArgumentCaptor.getValue());
        verify(bluetoothManager, atMost(1)).getCharacteristicGovernor(CHARACTERISTIC_1);
        verify(bluetoothManager).getCharacteristicGovernor(CHARACTERISTIC_2);
        verify(bluetoothManager).getDiscoveredAdapters();
        verify(bluetoothManager).notify(any(Runnable.class));

        verify(governorListener).ready(true);
        verify(governorListener).lastUpdatedChanged(LAST_INTERACTED);

        verifyNoMoreInteractions(bluetoothManager, governorListener, valueListener);
    }

    @Test
    public void testUpdateDelegatesBecomeReady() throws ExecutionException, InterruptedException {
        CompletableFuture<Boolean> ready = governor.whenReady(gov -> { return true; });
        assertFalse(ready.isDone());

        governor.init();

        governor.update();

        assertFalse(governor.isReady());
        assertFalse(ready.isDone());

        verify(delegate1, times(2)).isReady();
        verify(delegate2, times(2)).isReady();

        when(delegate2.isReady()).thenReturn(true);

        assertFalse(governor.isReady());
        verifyZeroInteractions(valueListener, governorListener);

        governor.update();

        assertTrue(governor.isReady());
        assertDelegateInstallation(delegate2);

        assertTrue(ready.isDone());
        assertTrue(ready.get());

        verify(bluetoothManager).addManagerListener(managerListenerArgumentCaptor.getValue());
        verify(bluetoothManager, atLeastOnce()).getCharacteristicGovernor(CHARACTERISTIC_1);
        verify(bluetoothManager, atLeastOnce()).getCharacteristicGovernor(CHARACTERISTIC_2);
        verify(bluetoothManager, atLeastOnce()).getDiscoveredAdapters();
        verify(bluetoothManager).notify(any(Runnable.class));

        verify(delegate1, atLeastOnce()).isReady();
        verify(delegate2, atLeastOnce()).isReady();

        verify(governorListener).ready(true);
        verify(governorListener).lastUpdatedChanged(LAST_INTERACTED);

        verifyNoMoreInteractions(bluetoothManager, delegate1, governorListener, valueListener);
    }

    @Test
    public void testDelegateManagerListener() {
        assertFalse(governor.isReady());
        governor.init();
        assertFalse(governor.isReady());

        when(delegate1.isReady()).thenReturn(true);
        managerListenerArgumentCaptor.getValue().ready(delegate1, true);

        assertTrue(governor.isReady());
        assertDelegateInstallation(delegate1);

        when(delegate1.isReady()).thenReturn(false);
        managerListenerArgumentCaptor.getValue().ready(delegate1, false);
        assertDelegateRemoval(delegate1);

        assertFalse(governor.isReady());
    }

    private void assertDelegateInstallation(CharacteristicGovernor delegate) {
        verify(delegate).addValueListener(valueListener);
        verify(delegate).addGovernorListener(governorListener);
        verify(delegate).getLastNotified();
        verify(delegate).getLastInteracted();
        verify(delegate, atLeastOnce()).isReady();
    }

    private void assertDelegateRemoval(CharacteristicGovernor delegate) {
        verify(delegate).removeValueListener(valueListener);
        verify(delegate).removeGovernorListener(governorListener);
        verify(delegate, atLeastOnce()).getLastNotified();
        verify(delegate, atLeastOnce()).getLastInteracted();
        verify(delegate, atLeastOnce()).isReady();
    }

}