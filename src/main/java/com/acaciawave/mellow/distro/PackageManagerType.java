package com.acaciawave.mellow.distro;

/** Package manager detected from the distribution. */
public enum PackageManagerType {
    APT,
    DNF,
    YUM,
    PACMAN,
    APK,
    UNKNOWN;

    public static PackageManagerType forFamily(DistroFamily family) {
        return switch (family) {
            case DEBIAN -> APT;
            case REDHAT -> DNF;
            case ARCH -> PACMAN;
            case ALPINE -> APK;
            case UNKNOWN -> UNKNOWN;
        };
    }
}