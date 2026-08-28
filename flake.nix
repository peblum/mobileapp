{
  description = "Pebble app Android CI toolchain";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
      androidPackages = pkgs.androidenv.composeAndroidPackages {
        buildToolsVersions = [ "36.0.0" ];
        platformVersions = [ "37" ];
        includeCmake = false;
        includeEmulator = false;
        includeNDK = false;
        includeSources = false;
        includeSystemImages = false;
      };
      androidSdk = androidPackages.androidsdk;
      androidHome = "${androidSdk}/libexec/android-sdk";
      androidBuildTools = "${androidHome}/build-tools/36.0.0";
      toolchainPackages = [
        pkgs.actionlint
        androidSdk
        pkgs.curl
        pkgs.forgejo-runner
        pkgs.git
        pkgs.jdk17
        pkgs.jq
        pkgs.libxml2
        pkgs.shellcheck
      ];
    in
    {
      checks.${system}.toolchain =
        pkgs.runCommand "pebble-app-ci-toolchain-check"
          {
            nativeBuildInputs = toolchainPackages;
          }
          ''
            export ANDROID_HOME=${androidHome}
            export ANDROID_SDK_ROOT=$ANDROID_HOME
            export JAVA_HOME=${pkgs.jdk17.home}
            export PATH=${androidBuildTools}:$ANDROID_HOME/platform-tools:$PATH

            java -version
            aapt2 version
            test -x "$(command -v apkanalyzer)"
            apksigner version
            zipalign_output="$(zipalign -h 2>&1 || true)"
            [[ "$zipalign_output" == *"Zip alignment utility"* ]]
            jq --version
            xmllint --version
            curl --version
            forgejo-runner --version
            git --version
            actionlint --version
            shellcheck --version
            test -f "$ANDROID_HOME/platforms/android-37/android.jar"

            touch "$out"
          '';

      devShells.${system}.default = pkgs.mkShell {
        packages = toolchainPackages;

        ANDROID_HOME = androidHome;
        ANDROID_SDK_ROOT = androidHome;
        JAVA_HOME = pkgs.jdk17.home;
        LANG = "C.UTF-8";
        LC_ALL = "C.UTF-8";

        shellHook = ''
          export PATH=${androidBuildTools}:$ANDROID_HOME/platform-tools:$PATH
        '';
      };
    };
}
