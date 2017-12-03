package org.sputnikdev.bluetooth.manager.impl;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.BluetoothObjectType;
import org.sputnikdev.bluetooth.manager.BluetoothObjectVisitor;
import org.sputnikdev.bluetooth.manager.GovernorListener;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BluetoothObjectGovernorTest {

    private static final URL URL= new URL("/11:22:33:44:55:66");

    private BluetoothObject bluetoothObject = mock(BluetoothObject.class);
    private BluetoothManagerImpl bluetoothManager = mock(BluetoothManagerImpl.class);

    @Mock
    private GovernorListener governorListener;

    @InjectMocks
    @Spy
    private BluetoothObjectGovernor governor = new BluetoothObjectGovernor(bluetoothManager, URL) {

        @Override void reset(BluetoothObject object) {
        }

        @Override void update(BluetoothObject object) {
        }

        @Override void init(BluetoothObject object) {
        }

        @Override public BluetoothObjectType getType() {
            return BluetoothObjectType.ADAPTER;
        }

        @Override public void accept(BluetoothObjectVisitor visitor) throws Exception {
        }
    };

    @Before
    public void setUp() {
        when(bluetoothObject.getURL()).thenReturn(URL.copyWithProtocol("tinyb"));
        when(bluetoothManager.getBluetoothObject(URL)).thenReturn(bluetoothObject);
    }

    @Test
    public void testGetBluetoothObject() throws Exception {
        assertEquals(bluetoothObject, governor.getBluetoothObject());
        verify(governor, times(1)).getBluetoothObject();
        verifyNoMoreInteractions(governor);
    }

    @Test(expected = NotReadyException.class)
    public void testGetBluetoothObjectNotReady() throws Exception {
        Whitebox.setInternalState(governor, "bluetoothObject", null);
        governor.getBluetoothObject();
    }

    @Test
    public void testUpdateNotReady() throws Exception {
        Whitebox.setInternalState(governor, "bluetoothObject", null);
        when(bluetoothManager.getBluetoothObject(URL)).thenReturn(null);

        governor.update();

        // check interactions
        InOrder inOrder = inOrder(governor, governorListener, bluetoothManager);

        //inOrder.verify(governor, times(1)).update();
        inOrder.verify(bluetoothManager, times(1)).getBluetoothObject(URL);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testUpdateNotReadyToReady() throws Exception {
        // conditions
        Whitebox.setInternalState(governor, "bluetoothObject", null);
        governor.addGovernorListener(governorListener);

        governor.update();

        InOrder inOrder = inOrder(governor, governorListener, bluetoothManager);
        inOrder.verify(bluetoothManager).getBluetoothObject(URL);
        inOrder.verify(governor).init(bluetoothObject);
        inOrder.verify(governorListener).ready(true);
        inOrder.verify(governor).update(bluetoothObject);
        inOrder.verify(governorListener, never()).lastUpdatedChanged(any());

        governor.updateLastChanged();
        governor.update();
        inOrder.verify(governorListener).lastUpdatedChanged(any());

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testUpdateReadyToNotReady() throws Exception {
        // conditions
        doThrow(Exception.class).when(governor).update(bluetoothObject);
        governor.addGovernorListener(governorListener);

        // invocation
        governor.update();

        // check interactions
        InOrder inOrder = inOrder(governor, governorListener);
        inOrder.verify(governor, times(1)).update(bluetoothObject);
        inOrder.verify(governor, times(1)).reset(bluetoothObject);
        inOrder.verify(governorListener, times(1)).ready(false);

        inOrder.verifyNoMoreInteractions();
        assertNull(Whitebox.getInternalState(governor, "bluetoothObject"));
    }

    @Test
    public void testGetURL() throws Exception {
        assertEquals(URL, governor.getURL());
        verify(governor, times(1)).getURL();
        verifyNoMoreInteractions(governor);
    }

    @Test
    public void testAddGovernorListener() throws Exception {
        governor.addGovernorListener(governorListener);
        verify(governor, times(1)).addGovernorListener(governorListener);
        verifyNoMoreInteractions(governor);
    }

    @Test
    public void testRemoveGovernorListener() throws Exception {
        governor.removeGovernorListener(governorListener);
        verify(governor, times(1)).removeGovernorListener(governorListener);
        verifyNoMoreInteractions(governor);
    }

    @Test
    public void testUpdateLastChanged() throws Exception {
        governor.addGovernorListener(governorListener);

        Date lastChanged = governor.getLastActivity();
        assertNull(lastChanged);

        Thread.sleep(1);
        governor.updateLastChanged();

        lastChanged = governor.getLastActivity();
        assertNotNull(lastChanged);

        Thread.sleep(1);
        governor.updateLastChanged();

        assertTrue(lastChanged.before(governor.getLastActivity()));
    }

    @Test
    public void testResetNotReady() throws Exception {
        Whitebox.setInternalState(governor, "bluetoothObject", null);
        governor.addGovernorListener(governorListener);

        governor.reset();

        // check interactions
        InOrder inOrder = inOrder(governor, governorListener);
        // side effect of using spy
        inOrder.verify(governor, times(1)).addGovernorListener(governorListener);
        inOrder.verify(governor).reset();

        // actual verification
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testResetReady() throws Exception {
        governor.addGovernorListener(governorListener);

        governor.reset();

        // check interactions
        InOrder inOrder = inOrder(governor, governorListener);
        inOrder.verify(governor, times(1)).reset(bluetoothObject);
        inOrder.verify(governorListener, times(1)).ready(false);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testIsReady() {
        assertTrue(governor.isReady());

        Whitebox.setInternalState(governor, "bluetoothObject", null);
        assertFalse(governor.isReady());
    }

    @Test
    public void testNotifyLastChanged() {
        Date date = new Date();
        Whitebox.setInternalState(governor, "lastActivity", date);
        governor.addGovernorListener(governorListener);

        governor.notifyLastChanged();

        verify(governorListener, times(1)).lastUpdatedChanged(date);
    }

    @Test
    public void testNotifyLastChangedException() {
        Date date = new Date();
        Whitebox.setInternalState(governor, "lastActivity", date);
        governor.addGovernorListener(governorListener);
        doThrow(Exception.class).when(governorListener).lastUpdatedChanged(any());

        governor.notifyLastChanged();

        verify(governorListener, times(1)).lastUpdatedChanged(date);
    }

    @Test
    public void testNotifyReady() {
        governor.addGovernorListener(governorListener);

        governor.notifyReady(true);

        verify(governorListener, times(1)).ready(true);
    }

    @Test
    public void testNotifyReadyException() {
        governor.addGovernorListener(governorListener);
        doThrow(Exception.class).when(governorListener).ready(true);

        governor.notifyReady(true);

        verify(governorListener, times(1)).ready(true);
    }

}
