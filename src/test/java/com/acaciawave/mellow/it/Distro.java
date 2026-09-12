package com.acaciawave.mellow.it;

/**
 * Real Linux distributions used for end-to-end CLI validation. Each entry knows
 * how to build an SSH-capable image of itself.
 */
public enum Distro {

    UBUNTU("ubuntu:24.04", Family.APT),
    DEBIAN("debian:12", Family.APT),
    ALPINE("alpine:3.20", Family.APK),
    FEDORA("fedora:41", Family.DNF);

    public enum Family {
        APT,
        APK,
        DNF
    }

    private final String baseImage;
    private final Family family;

    Distro(String baseImage, Family family) {
        this.baseImage = baseImage;
        this.family = family;
    }

    public String key() {
        return name().toLowerCase();
    }

    public String baseImage() {
        return baseImage;
    }

    public Family family() {
        return family;
    }

    /** Dockerfile that turns the base image into a root-login SSH server. */
    String dockerfile() {
        String install = switch (family) {
            case APT -> """
                    ENV DEBIAN_FRONTEND=noninteractive
                    RUN apt-get update \\
                     && apt-get install -y --no-install-recommends openssh-server sudo ca-certificates curl gnupg \\
                     && rm -rf /var/lib/apt/lists/*
                    RUN mkdir -p /run/sshd
                    """;
            case APK -> """
                    RUN apk add --no-cache openssh sudo shadow ca-certificates curl bash
                    """;
            case DNF -> """
                    RUN dnf install -y openssh-server sudo ca-certificates curl shadow-utils \\
                     && dnf clean all
                    """;
        };

        return "FROM " + baseImage + "\n"
                + install
                + "RUN ssh-keygen -A\n"
                + "RUN echo 'root:" + SshDistro.ROOT_PASSWORD + "' | chpasswd\n"
                + sshdConfig()
                + "EXPOSE 22\n"
                + "CMD [\"/usr/sbin/sshd\", \"-D\", \"-e\"]\n";
    }

    /**
     * APT/DNF ship a trailing {@code Match} block in {@code sshd_config}, so
     * appending directives there would scope them to that match. Use a drop-in
     * file (included first) instead; Alpine has no include dir, so append.
     */
    private String sshdConfig() {
        return switch (family) {
            case APT, DNF -> "RUN mkdir -p /etc/ssh/sshd_config.d && printf 'PermitRootLogin yes\\nPasswordAuthentication yes\\n'"
                    + " > /etc/ssh/sshd_config.d/99-mellow.conf\n";
            case APK -> "RUN printf '\\nPermitRootLogin yes\\nPasswordAuthentication yes\\n' >> /etc/ssh/sshd_config\n";
        };
    }
}