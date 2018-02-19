package org.sputnikdev.bluetooth.manager.impl;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

class DeferredCompletableFuture<G, T> extends CompletableFuture<T> {

    private final Function<G, T> function;

    DeferredCompletableFuture(Function<G, T> function) {
        this.function = function;
    }

    Function<G, T> getFunction() {
        return function;
    }
}
