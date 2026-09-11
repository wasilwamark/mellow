package com.acaciawave.mellow.distro;

/** Linux distribution family. */
public enum DistroFamily {
    DEBIAN,
    REDHAT,
    ARCH,
    ALPINE,
    UNKNOWN;

    public static DistroFamily fromId(String id) {
        if (id == null) {
            return UNKNOWN;
        }
        return switch (id.toLowerCase()) {
            case "debian", "ubuntu", "linuxmint", "pop", "kali", "raspbian" -> DEBIAN;
            case "rhel", "centos", "fedora", "rocky", "almalinux", "amzn" -> REDHAT;
            case "arch", "manjaro", "endeavouros" -> ARCH;
            case "alpine" -> ALPINE;
            default -> UNKNOWN;
        };
    }
}