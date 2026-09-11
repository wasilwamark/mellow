package com.acaciawave.mellow.cli;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Direct command-provider registry. Replaces the Go plugin loader/registry:
 * providers are registered explicitly at startup and looked up by name.
 */
public final class Registry {

    private final Map<String, Provider> providers = new LinkedHashMap<>();

    public void register(Provider provider) {
        providers.putIfAbsent(provider.name(), provider);
    }

    public Optional<Provider> get(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    public boolean contains(String name) {
        return providers.containsKey(name);
    }

    /** All providers in registration order. */
    public Collection<Provider> all() {
        return providers.values();
    }

    public int size() {
        return providers.size();
    }
}