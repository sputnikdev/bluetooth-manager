package org.sputnikdev.bluetooth.manager.impl;

import org.slf4j.Logger;

import java.util.List;
import java.util.function.BiConsumer;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;

public class MockUtils {

    public static void mockImplicitNotifications(BluetoothManagerImpl bluetoothManager) {
        doAnswer(answer -> {
            try {
                ((Runnable) answer.getArguments()[0]).run();
            } catch (Exception ignore) { }
            return null;
        }).when(bluetoothManager).notify(any(Runnable.class));
        doAnswer(answer -> {
            List listeners = (List) answer.getArguments()[0];
            Object value = answer.getArguments()[2];
            BiConsumer consumer = (BiConsumer) answer.getArguments()[1];
            listeners.forEach(listener -> {
                try {
                    consumer.accept(listener, value);
                } catch (Exception ignore) { }
            });
            return null;
        }).when(bluetoothManager).notify(anyList(), any(BiConsumer.class), any(),
                any(Logger.class), anyString());
    }

}
