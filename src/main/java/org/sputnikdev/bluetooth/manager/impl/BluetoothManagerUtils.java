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

import org.slf4j.Logger;
import org.sputnikdev.bluetooth.URL;
import org.sputnikdev.bluetooth.manager.transport.BluetoothObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Utility class.
 * @author Vlad Kolotov
 */
final class BluetoothManagerUtils {

    private static final Pattern MAC_PATTERN = Pattern.compile("(\\w\\w[:-]){5}\\w\\w");

    private BluetoothManagerUtils() { }

    static List<URL> getURLs(List<? extends BluetoothObject> objects) {
        List<URL> urls = new ArrayList<>(objects.size());
        for (BluetoothObject object : objects) {
            urls.add(object.getURL());
        }
        return Collections.unmodifiableList(urls);
    }

    static <T> void forEachSilently(Collection<T> listeners, Consumer<T> consumer,
                                       Logger logger, String error) {
        forEachSilently(listeners, consumer, ex -> {
            logger.error(error, ex);
        });
    }

    static <T, V> void forEachSilently(Collection<T> listeners, BiConsumer<T, V> consumer, V value,
                                       Logger logger, String error) {
        forEachSilently(listeners, consumer, value, ex -> {
            logger.error(error, ex);
        });
    }

    static <T> void forEachSilently(Collection<T> objects, Consumer<T> func, Consumer<Exception> errorHandler) {
        objects.forEach(deviceDiscoveryListener -> {
            try {
                func.accept(deviceDiscoveryListener);
            } catch (Exception ex) {
                errorHandler.accept(ex);
            }
        });
    }

    static <T, V> void forEachSilently(Collection<T> objects, BiConsumer<T, V> func, V value,
                                               Consumer<Exception> errorHandler) {
        objects.forEach(deviceDiscoveryListener -> {
            try {
                func.accept(deviceDiscoveryListener, value);
            } catch (Exception ex) {
                errorHandler.accept(ex);
            }
        });
    }

    static Instant max(Instant first, Instant second) {
        if (first == null && second == null) {
            return null;
        }
        if (first != null && second == null) {
            return first;
        }
        if (first == null) {
            return second;
        }
        return first.isAfter(second) ? first : second;
    }

    static boolean isMacAddress(String name) {
        return name != null && MAC_PATTERN.matcher(name).matches();
    }

}
