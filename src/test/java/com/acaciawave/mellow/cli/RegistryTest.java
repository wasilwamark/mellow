package com.acaciawave.mellow.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.acaciawave.mellow.MellowException;

class RegistryTest {

    private static Provider provider(String name) {
        return new Provider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return name + " provider";
            }

            @Override
            public List<Command> commands() {
                return List.of(Command.of("ping", "ping", context -> {
                }));
            }
        };
    }

    @Test
    void registersAndLooksUpProviders() {
        Registry registry = new Registry();
        registry.register(provider("system"));
        registry.register(provider("nginx"));

        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.contains("nginx")).isTrue();
        assertThat(registry.get("nginx")).isPresent();
        assertThat(registry.get("missing")).isEmpty();
    }

    @Test
    void keepsRegistrationOrder() {
        Registry registry = new Registry();
        registry.register(provider("alias"));
        registry.register(provider("system"));
        registry.register(provider("nginx"));

        assertThat(registry.all()).extracting(Provider::name)
                .containsExactly("alias", "system", "nginx");
    }

    @Test
    void duplicateRegistrationIsIgnored() {
        Registry registry = new Registry();
        registry.register(provider("system"));
        registry.register(provider("system"));
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void parsesTargetsWithOptionalPort() {
        assertThat(Dispatcher.Target.parse("ubuntu@1.2.3.4"))
                .isEqualTo(new Dispatcher.Target("ubuntu", "1.2.3.4", 22));
        assertThat(Dispatcher.Target.parse("root@example.com:2222"))
                .isEqualTo(new Dispatcher.Target("root", "example.com", 2222));
    }

    @Test
    void rejectsMalformedTargets() {
        assertThatThrownBy(() -> Dispatcher.Target.parse("no-at-sign"))
                .isInstanceOf(MellowException.class);
        assertThatThrownBy(() -> Dispatcher.Target.parse("@1.2.3.4"))
                .isInstanceOf(MellowException.class);
    }
}