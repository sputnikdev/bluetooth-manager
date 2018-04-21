package org.sputnikdev.bluetooth.manager.impl;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

class DeferredCompletableFuture<G, T> extends CompletableFuture<T> {

    private final Predicate<G> predicate;
    private final Function<G, T> function;

    DeferredCompletableFuture(Predicate<G> predicate, Function<G, T> function) {
        this.predicate = predicate;
        this.function = function;
    }

    public Predicate<G> getPredicate() {
        return predicate;
    }

    Function<G, T> getFunction() {
        return function;
    }

}
