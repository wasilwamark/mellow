package com.acaciawave.mellow.pkgmgr;

import java.util.StringJoiner;

import com.acaciawave.mellow.distro.DistroInfo;
import com.acaciawave.mellow.distro.PackageManagerType;

/** Factory + implementations for the supported package managers. */
public final class PackageManagers {

    private PackageManagers() {
    }

    public static PackageManager forDistro(DistroInfo distro) {
        return switch (distro.packageManager()) {
            case APT -> new Apt();
            case DNF -> new Dnf();
            case YUM -> new Yum();
            case PACMAN -> new Pacman();
            case APK -> new Apk();
            case UNKNOWN -> new Apt(); // Debian-family fallback, matching Go
        };
    }

    private static String join(String... packages) {
        StringJoiner joiner = new StringJoiner(" ");
        for (String pkg : packages) {
            joiner.add(pkg);
        }
        return joiner.toString();
    }

    static final class Apt implements PackageManager {
        @Override public String update() { return "apt-get update -y"; }
        @Override public String upgrade() { return "apt-get upgrade -y"; }
        @Override public String distUpgrade() { return "apt-get dist-upgrade -y"; }
        @Override public String autoremove() { return "apt-get autoremove -y"; }

        @Override public String install(String... packages) {
            requirePackages("install", packages);
            return "DEBIAN_FRONTEND=noninteractive apt-get install -y " + join(packages);
        }

        @Override public String remove(String... packages) {
            requirePackages("remove", packages);
            return "apt-get remove -y " + join(packages);
        }
    }

    static final class Dnf implements PackageManager {
        // `dnf check-update` exits 100 when updates are available, which is not a
        // failure; makecache refreshes metadata and returns 0.
        @Override public String update() { return "dnf makecache -y"; }
        @Override public String upgrade() { return "dnf upgrade -y"; }
        @Override public String distUpgrade() { return "dnf distro-sync -y"; }
        @Override public String autoremove() { return "dnf autoremove -y"; }

        @Override public String install(String... packages) {
            requirePackages("install", packages);
            return "dnf install -y " + join(packages);
        }

        @Override public String remove(String... packages) {
            requirePackages("remove", packages);
            return "dnf remove -y " + join(packages);
        }
    }

    static final class Yum implements PackageManager {
        @Override public String update() { return "yum check-update -y"; }
        @Override public String upgrade() { return "yum update -y"; }
        @Override public String distUpgrade() { return "yum update -y"; }
        @Override public String autoremove() { return "yum autoremove -y"; }

        @Override public String install(String... packages) {
            requirePackages("install", packages);
            return "yum install -y " + join(packages);
        }

        @Override public String remove(String... packages) {
            requirePackages("remove", packages);
            return "yum remove -y " + join(packages);
        }
    }

    static final class Pacman implements PackageManager {
        @Override public String update() { return "pacman -Sy --noconfirm"; }
        @Override public String upgrade() { return "pacman -Su --noconfirm"; }
        @Override public String distUpgrade() { return "pacman -Syu --noconfirm"; }
        @Override public String autoremove() { return "pacman -Rns $(pacman -Qtdq) --noconfirm"; }

        @Override public String install(String... packages) {
            requirePackages("install", packages);
            return "pacman -S --noconfirm " + join(packages);
        }

        @Override public String remove(String... packages) {
            requirePackages("remove", packages);
            return "pacman -R --noconfirm " + join(packages);
        }
    }

    static final class Apk implements PackageManager {
        @Override public String update() { return "apk update"; }
        @Override public String upgrade() { return "apk upgrade"; }
        @Override public String distUpgrade() { return "apk upgrade"; }
        @Override public String autoremove() { return "apk cache clean"; }

        @Override public String install(String... packages) {
            requirePackages("install", packages);
            return "apk add " + join(packages);
        }

        @Override public String remove(String... packages) {
            requirePackages("remove", packages);
            return "apk del " + join(packages);
        }
    }
}