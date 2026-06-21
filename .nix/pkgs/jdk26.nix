{
  stdenv,
  lib,
  callPackage,
  nixpkgsSrc,
}:

let
  temurinPath = "${nixpkgsSrc}/pkgs/development/compilers/temurin-bin";

  linuxSources = {
    packageType = "jdk";
    vmType = "hotspot";
    x86_64 = {
      build = "8";
      sha256 = "8e512f13e575a43655fc92319436c94890c137b9035cc6bd6f9cf24239704d3a";
      url = "https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.1%2B8/OpenJDK26U-jdk_x64_linux_hotspot_26.0.1_8.tar.gz";
      version = "26.0.1";
    };
    aarch64 = {
      build = "8";
      sha256 = "613f9b2861dea937b24d5eca745ef8567733b377d0bb612195acaad0e3f61360";
      url = "https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.1%2B8/OpenJDK26U-jdk_aarch64_linux_hotspot_26.0.1_8.tar.gz";
      version = "26.0.1";
    };
  };

  darwinSources = {
    packageType = "jdk";
    vmType = "hotspot";
    x86_64 = {
      build = "8";
      sha256 = "032df492c8749864ee8b135e3d0ea5a17ef5c847309d5fdf8f1dd55e246b8319";
      url = "https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.1%2B8/OpenJDK26U-jdk_x64_mac_hotspot_26.0.1_8.tar.gz";
      version = "26.0.1";
    };
    aarch64 = {
      build = "8";
      sha256 = "10c436258e24693ce8baf13dbffce98b77275797455e8b72510967547f13234a";
      url = "https://github.com/adoptium/temurin26-binaries/releases/download/jdk-26.0.1%2B8/OpenJDK26U-jdk_aarch64_mac_hotspot_26.0.1_8.tar.gz";
      version = "26.0.1";
    };
  };

  baseFile =
    if stdenv.hostPlatform.isLinux then
      "${temurinPath}/jdk-linux-base.nix"
    else if stdenv.hostPlatform.isDarwin then
      "${temurinPath}/jdk-darwin-base.nix"
    else
      throw "Unsupported platform for JDK 26";

  sources = if stdenv.hostPlatform.isLinux then linuxSources else darwinSources;
in
callPackage (import baseFile { sourcePerArch = sources; }) { }
