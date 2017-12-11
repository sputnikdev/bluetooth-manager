package org.sputnikdev.bluetooth.manager.impl;

/*-
 * #%L
 * org.sputnikdev:bluetooth-manager
 * %%
 * Copyright (C) 2017 Sputnik Dev
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Utility class.
 * @author Vlad Kolotov
 */
final class BluetoothManagerUtils {

    private BluetoothManagerUtils() { }

    static List<URL> getURLs(List<? extends BluetoothObject> objects) {
        List<URL> urls = new ArrayList<>(objects.size());
        for (BluetoothObject object : objects) {
            urls.add(object.getURL());
        }
        return Collections.unmodifiableList(urls);
    }

    static <T> void safeForEachError(Collection<T> listeners, Consumer<T> func, org.slf4j.Logger logger, String error) {
        safeForEach(listeners, func, ex -> {
            logger.warn(error, ex);
        });
    }

    static <T> void safeForEachInfo(Collection<T> listeners, Consumer<T> func, org.slf4j.Logger logger, String info) {
        safeForEach(listeners, func, ex -> {
            logger.warn(info, ex);
        });
    }

    static <T> void safeForEachWarn(Collection<T> listeners, Consumer<T> func, org.slf4j.Logger logger, String warn) {
        safeForEach(listeners, func, ex -> {
            logger.warn(warn, ex);
        });
    }


    static <T> void safeForEach(Collection<T> objects, Consumer<T> func, Consumer<Exception> errorHandler) {
        objects.forEach(deviceDiscoveryListener -> {
            try {
                func.accept(deviceDiscoveryListener);
            } catch (Exception ex) {
                errorHandler.accept(ex);
            }
        });
    }

    static void setState(AtomicLong state, int index, boolean newState, Runnable changed) {
        setState(state, index, newState, changed, null);
    }

    static void setState(AtomicLong state, int index, boolean newState, Runnable changed, Runnable notChanged) {
        state.getAndUpdate(current -> {
            long updated = newState ? current | (1 << index) : current & ~(1 << index);
            if (updated == 0 && current > 0 || updated != 0 && current == 0) {
                changed.run();
            } else if (notChanged != null) {
                notChanged.run();
            }
            return updated;
        });
    }

}
