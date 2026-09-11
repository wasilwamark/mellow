package com.acaciawave.mellow.pkgmgr;

import com.acaciawave.mellow.MellowException;

/** Distro package-manager abstraction (port of {@code pkgmgr.PackageManager}). */
public interface PackageManager {

    String update();

    String upgrade();

    String distUpgrade();

    String autoremove();

    String install(String... packages);

    String remove(String... packages);

    /** Rejects empty package lists like the Go implementation did. */
    default void requirePackages(String action, String... packages) {
        if (packages == null || packages.length == 0) {
            throw new MellowException("no packages specified for " + action);
        }
    }
}