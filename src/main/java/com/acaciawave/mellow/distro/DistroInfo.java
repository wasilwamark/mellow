package com.acaciawave.mellow.distro;

/**
 * Detected distribution information (port of {@code distro.DistroInfo}).
 */
public record DistroInfo(
        String id,
        String name,
        String version,
        DistroFamily family,
        PackageManagerType packageManager) {

    public static DistroInfo unknown() {
        return new DistroInfo("unknown", "Unknown", "", DistroFamily.UNKNOWN, PackageManagerType.UNKNOWN);
    }

    public boolean isDebian() {
        return family == DistroFamily.DEBIAN;
    }

    public boolean isRedHat() {
        return family == DistroFamily.REDHAT;
    }

    public boolean isUbuntu() {
        return "ubuntu".equalsIgnoreCase(id);
    }

    public boolean isCentOS() {
        return "centos".equalsIgnoreCase(id);
    }

    /** Human-readable summary used in command output. */
    public String display() {
        return version == null || version.isBlank() ? name : name + " " + version;
    }
}