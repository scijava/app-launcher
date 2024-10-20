[![](https://img.shields.io/maven-central/v/org.scijava/app-launcher.svg)](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.scijava%22%20AND%20a%3A%22app-launcher%22)
[![](https://github.com/scijava/app-launcher/actions/workflows/build.yml/badge.svg)](https://github.com/scijava/app-launcher/actions/workflows/build.yml)

The SciJava app launcher provides an entry point into SciJava applications,
notably [Fiji](https://fiji.sc/).

Its most major functions are:

* Ensure the version of Java being used is appropriate to app's requirements.
  In case it isn't, offer to upgrade Java by downloading a more appropriate version.

* Load classes dynamically, without relying on the system classpath.

* Display a splash window while the application is starting up.

## Supported configuration

The app-launcher uses system properties to configure its behavior:

* `scijava.app.name`: Name of the application being launched.
  Used in message dialogs if/when interacting with the user.

* `scijava.app.splash-image`: Resource path to an image for the splash window,
  to be loaded using `ClassLoader#getResource`. It can be either its own file
  or stored within a JAR file, as long as the resource is on the classpath.

* `scijava.app.look-and-feel`: Fully qualified class name of a Swing
  `LookAndFeel` to be set before any UI components are created or shown.
  This can be useful to ensure a consistent appearance between the app-launcher
  splash and dialog UI with any later application UI, as well as to improve UI
  behavior on HiDPI displays using smarter Look & Feels such as
  [FlatLaf](https://www.formdev.com/flatlaf/).

* `scijava.app.java-root`: directory containing "bundled" installations of Java.
  The `Java.root()` method reports this value if it points to a valid directory.
  The `Java.check()` method will look here (via `Java.root()`) for which JVMs
  are already present locally, and also unpack any newly downloaded JVM into
  this directory.

* `scijava.app.java-links`: URL of a plain text file containing links to
  desirable Java bundles. The format is `<platform>=<url>` for each platform
  (OS+architecture) of Java that you want to support. For example:
  ```
  linux-arm64=https://cdn.azul.com/zulu/bin/zulu21.36.17-ca-jdk21.0.4-linux_aarch64.tar.gz
  linux-x64=https://cdn.azul.com/zulu/bin/zulu21.36.17-ca-jdk21.0.4-linux_x64.tar.gz
  macos-arm64=https://cdn.azul.com/zulu/bin/zulu21.36.17-ca-jdk21.0.4-macosx_aarch64.tar.gz
  macos-x64=https://cdn.azul.com/zulu/bin/zulu21.36.17-ca-jdk21.0.4-macosx_x64.tar.gz
  windows-arm64=https://cdn.azul.com/zulu/bin/zulu21.36.17-ca-jdk21.0.4-win_aarch64.zip
  windows-x64=https://cdn.azul.com/zulu/bin/zulu21.36.17-ca-jdk21.0.4-win_x64.zip
  ```
  The exact naming is up to you, but for a Java distribution to be downloaded,
  the `scijava.app.platform` property must be set and match one of the keys
  indicated within the fetched remote resource.

* `scijava.app.java-version-minimum`:  The minimum version of Java required by
  the application. It can be a standalone number like 11, in which case it is
  treated as a major version, or a dot-separated sequence of digits, which case
  version comparisons are done digit by digit (see `Versions.compare`).
  This value is used by `Java.check()` (via `Java.minimumVersion()`) to
  warn the user accordingly if the running Java version is not good enough.

* `scijava.app.java-version-recommended`: The minimum version of Java the
  application would *prefer* to use. Same syntax as `java-version-minimum`.
  This value is used by `Java.check()` (via `Java.recommendedVersion()`) to
  warn the user accordingly if the running Java version is not ideal.

## Provenance

The SciJava app-launcher evolved from the
[ImageJ Launcher](https://github.com/imagej/imagej-launcher),
which was a prior solution for launching [Fiji](https://fiji.sc/).

The imagej-launcher's `ClassLauncher` supported a couple of
Fiji/ImageJ-specific flags that this SciJava app-launcher does not:

**`-ijjarpath <path>`:** This flag provided automatic loading of plugins in
`$HOME/.plugins` as well as from the value(s) of the `ij1.plugin.dirs` system
property, when `<path>` was set to `plugins`. To accomplish an equivalent thing
with the app-launcher, use something like:
```shell
-jarpath plugins:"$HOME"/.plugins:/path1:/path2:/path3
```
rather than:
```shell
-Dij1.plugin.dirs=/path1:/path2:/path3 -jarpath plugins
```

This works because the `org.scijava.launcher.ClassLauncher`'s `-jarpath`
handling splits on colons/semicolons.

**`-ijcp <path1:path2:...>`:** This was an undocumented feature, not used by
normal Fiji/ImageJ launches. You can accomplish something similar using
`-cp <path1:path2:...>` instead.
