package org.sputnikdev.bluetooth.manager.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sputnikdev.bluetooth.manager.BluetoothGovernor;
import org.sputnikdev.bluetooth.manager.BluetoothInteractionException;
import org.sputnikdev.bluetooth.manager.NotReadyException;
import org.sputnikdev.bluetooth.manager.auth.BluetoothAuthenticationException;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Predicate;

class CompletableFutureService<G extends BluetoothGovernor> {

    private Logger logger = LoggerFactory.getLogger(CompletableFutureService.class);

    private final ConcurrentLinkedQueue<DeferredCompletableFuture<G, ?>> futures =
            new ConcurrentLinkedQueue<>();

    <V> CompletableFuture<V> submit(G governor, Predicate<G> predicate, Function<G, V> function) {
        DeferredCompletableFuture<G, V> future = new DeferredCompletableFuture<>(predicate, function);

        try {
            if (!predicate.test(governor)) {
                logger.debug("Future is not ready to be completed immediately: {} : {}", governor.getURL(), predicate);
                futures.add(future);
                return future;
            }

            logger.debug("Trying to complete future immediately: {} : {}", governor.getURL(), predicate);

            future.complete(function.apply(governor));
        } catch (BluetoothInteractionException | NotReadyException | BluetoothAuthenticationException nativeException) {
            logger.warn("Bluetooth error happened while completing a future immediately: {} : {}",
                    governor.getURL(), nativeException.getMessage());
            futures.add(future);
        } catch (Exception ex) {
            logger.error("Application error happened while completing a ready future: {}", governor.getURL(), ex);
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
    void complete(G governor) {
        logger.trace("Trying to complete futures: {} : {}", governor.getURL(), futures.size());

        for (Iterator<DeferredCompletableFuture<G, ?>> iterator = futures.iterator(); iterator.hasNext(); ) {
            DeferredCompletableFuture next = iterator.next();
            if (next.isCancelled()) {
                continue;
            }

            try {
                if (!next.getPredicate().test(governor)) {
                    continue;
                }
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
