package org.sputnikdev.bluetooth.manager.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothInteractionException;
import org.sputnikdev.bluetooth.manager.NotReadyException;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class CompletableFutureService<G extends BluetoothGovernor> {

    private Logger logger = LoggerFactory.getLogger(CompletableFutureService.class);

    private final ConcurrentLinkedQueue<DeferredCompletableFuture<G, ?>> futures =
            new ConcurrentLinkedQueue<>();

    <V> CompletableFuture<V> submit(G governor, Function<G, V> function) {
        DeferredCompletableFuture<G, V> future = new DeferredCompletableFuture<>(function);

        logger.debug("Trying to complete future immediately: {}", governor.getURL());
        try {
            future.complete(function.apply(governor));
        } catch (BluetoothInteractionException | NotReadyException nativeException) {
            logger.warn("Bluetooth error happened while competing a future immediately: {} : {}",
                    governor.getURL(), nativeException.getMessage());
            futures.add(future);
        } catch (Exception ex) {
            logger.warn("Application error happened while competing a ready future: {} : {}",
                    governor.getURL(), ex.getMessage());
            future.completeExceptionally(ex);
        }

        return future;
    }

    void completeSilently(G governor) {
        try {
            complete(governor);
        } catch (Exception ex) {
            logger.warn("Error occurred while completing (silently) futures: {}", ex.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void complete(G governor) throws BluetoothInteractionException, NotReadyException {
        logger.debug("Trying to complete futures: {} : {}", governor.getURL(), futures.size());
        for (Iterator<DeferredCompletableFuture<G, ?>> iterator = futures.iterator();
             iterator.hasNext(); ) {
            DeferredCompletableFuture next = iterator.next();
            if (next.isCancelled()) {
                iterator.remove();
                continue;
            }
            try {
                next.complete(next.getFunction().apply(governor));
            } catch (BluetoothInteractionException | NotReadyException nativeException) {
                logger.warn("Bluetooth error happened while competing a future: {} : {}",
                        governor.getURL(), nativeException.getMessage());
                // put affecting future to the end
                iterator.remove();
                futures.add(next);
                throw nativeException;
            } catch (Exception ex) {
                logger.warn("Application error happened while competing a future: {} : {}",
                        governor.getURL(), ex.getMessage());
                next.completeExceptionally(ex);
            }
            iterator.remove();
        }
    }

    void clear() {
        futures.forEach(future -> future.cancel(true));
        futures.clear();
    }

}
