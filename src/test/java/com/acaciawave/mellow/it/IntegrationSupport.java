package com.acaciawave.mellow.it;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * Starts only the distro containers selected via {@code -Dit.distros=...}
 * (default: all), and skips the whole class when Docker is unavailable.
 *
 * <p>Starting containers on demand — rather than {@code @Container} fields —
 * keeps memory bounded in CI, where several distros plus their package installs
 * would otherwise run at once.</p>
 */
abstract class IntegrationSupport {

    private static final Map<Distro, GenericContainer<?>> CONTAINERS = new EnumMap<>(Distro.class);

    @BeforeAll
    static void startContainers() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available; skipping integration tests");
        for (Distro distro : selectedDistros()) {
            GenericContainer<?> container = SshDistro.container(distro);
            container.start();
            CONTAINERS.put(distro, container);
        }
    }

    @AfterAll
    static void stopContainers() {
        CONTAINERS.values().forEach(GenericContainer::stop);
        CONTAINERS.clear();
    }

    protected static GenericContainer<?> container(Distro distro) {
        return CONTAINERS.get(distro);
    }

    protected static List<Distro> selectedDistros() {
        String property = System.getProperty("it.distros", "ubuntu,debian,alpine,fedora");
        return Stream.of(property.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(value -> Distro.valueOf(value.toUpperCase(Locale.ROOT)))
                .toList();
    }
}