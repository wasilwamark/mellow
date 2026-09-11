package com.acaciawave.mellow.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReplTest {

    @Test
    void tokenizesPlainWords() {
        assertThat(Repl.tokenize("myserver system update"))
                .containsExactly("myserver", "system", "update");
    }

    @Test
    void tokenizesQuotedArguments() {
        assertThat(Repl.tokenize("alias add ovh ubuntu@1.2.3.4 --password 's3 cr3t'"))
                .containsExactly("alias", "add", "ovh", "ubuntu@1.2.3.4", "--password", "s3 cr3t");
        assertThat(Repl.tokenize("add-site \"example.com\" --proxy 3000"))
                .containsExactly("add-site", "example.com", "--proxy", "3000");
    }

    @Test
    void tokenizesEmptyAndWhitespace() {
        assertThat(Repl.tokenize("")).isEmpty();
        assertThat(Repl.tokenize("    ")).isEmpty();
    }

    @Test
    void keepsEmptyQuotedArgument() {
        assertThat(Repl.tokenize("cmd '' next")).containsExactly("cmd", "", "next");
    }
}