package org.sputnikdev.bluetooth.manager.util;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.Characteristic;
import org.sputnikdev.bluetooth.manager.transport.CharacteristicAccessType;
import org.sputnikdev.bluetooth.manager.transport.Notification;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CharacteristicEmulator {

    private Characteristic characteristic;
    private ArgumentCaptor<Notification> valueNotificationCaptor = ArgumentCaptor.forClass(Notification.class);

    public CharacteristicEmulator(URL url, CharacteristicAccessType... flags) {
        characteristic = mock(Characteristic.class);
        when(characteristic.getURL()).thenReturn(url);
        when(characteristic.getFlags()).thenReturn(Stream.of(flags).collect(Collectors.toSet()));

        Mockito.doAnswer(answer -> {
            when(characteristic.isNotifying()).thenReturn(true);
            return true;
        }).when(characteristic).enableValueNotifications(valueNotificationCaptor.capture());
    }

    public Characteristic getCharacteristic() {
        return characteristic;
    }

    public void whenWritten(byte[] data, Runnable then) {
        when(characteristic.writeValue(data)).thenAnswer(answer -> {
            CompletableFuture.runAsync(then);
            return true;
        });
    }

    public void notify(byte[] value) {
        valueNotificationCaptor.getValue().notify(value);
    }

}
