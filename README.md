[![](https://img.shields.io/maven-central/v/org.scijava/app-launcher.svg)](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.scijava%22%20AND%20a%3A%22app-launcher%22)
[![](https://github.com/scijava/app-launcher/actions/workflows/build.yml/badge.svg)](https://github.com/scijava/app-launcher/actions/workflows/build.yml)

The SciJava app-launcher provides an entry point into SciJava applications,
notably [Fiji](https://fiji.sc/). Its design synergizes well with the
[Jaunch](https://github.com/apposed/jaunch) native launcher, although
Jaunch is by no means the only solution for the native executable side;
[`jpackage`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html)
or [jDeploy](https://www.jdeploy.com/) should also work for example.

## Major features

### Java version checking and upgrade

The app-launcher verifies that the running JVM meets the application's minimum
and recommended Java version requirements, as specified by
`scijava.app.java-version-minimum` and `scijava.app.java-version-recommended`,
respectively. If the version is too old, the user is offered a choice: download
and install a newer Java automatically, switch to an already-present
good-enough installation, or proceed anyway. The download URL is resolved
per-platform from the file pointed to by `scijava.app.java-links`, and the new
installation path is written back into the config file indicated by
`scijava.app.config-file` so that the native launcher can pick it up on the
next start.

### Splash window

While the application initializes, the app-launcher displays a lightweight
splash window with an optional logo image (given by `scijava.app.splash-image`)
and a progress bar. The window closes automatically once a window from the main
application appears on screen.

### Single-instance enforcement

When `scijava.app.single-instance` is `true`, only one JVM instance of the
application runs at a time. The first instance opens a TCP server socket and
writes the port number and a 128-bit random secret into a private lockfile; any
subsequent launch reads the lockfile, forwards its command-line arguments to
the running instance over the socket, and exits without showing the splash
screen. The running instance receives the arguments and acts on them as
appropriate (e.g. by opening files). The wire protocol is line-oriented plain
text; the client sends the secret as the first line, then one argument per
line, then closes the connection.

### Module unlocking

When `scijava.app.unlock-modules` is `true`, the app-launcher calls
`ReflectionUnlocker.unlockAll()` before doing anything else. This defeats
Java 9+ JPMS encapsulation by calling `implAddOpensToAllUnnamed()` on every
module, allowing deep reflection without needing `--add-opens` JVM arguments
except for a single `--add-opens=java.base/java.lang=ALL-UNNAMED` argument.

### Swing Look & Feel configuration

Setting `scijava.app.look-and-feel` to a fully-qualified class name causes the
app-launcher to install that `LookAndFeel` before creating any UI components.
This keeps the splash and error dialogs visually consistent with the main
application, and enables smarter HiDPI handling via Look & Feels such as
[FlatLaf](https://www.formdev.com/flatlaf/).

### Class launching

`ClassLauncher` is the main entry point. It accepts the target class name and
passes any remaining arguments to that class's `main` method. If the target
class fails to load because it was compiled for a newer JVM
(`UnsupportedClassVersionError`), the launcher catches the error and offers the
user a Java upgrade before exiting.

## Supported configuration

The app-launcher uses system properties to configure its behavior:

* `scijava.app.name`: Human-readable name of the application being launched.
  Used in splash progress messages and in user-facing dialogs ("Please restart
  Fizzbuzz to apply the changes", "Fizzbuzz has been successfully updated to
  use the newer Java", etc.).

* `scijava.app.directory`: Path to the application's installation directory.
  Used to convert Java installation paths to relative form when writing to the
  config file, improving portability when the app is moved or accessed from
  multiple operating systems. If unset or pointing to a nonexistent path,
  absolute paths are used instead.

* `scijava.app.splash-image`: Resource path to an image for the splash window,
  to be loaded using `ClassLoader#getResource`. It can be either its own file
  or stored within a JAR file, as long as the resource is on the classpath.

* `scijava.app.look-and-feel`: Fully qualified class name of a Swing
  `LookAndFeel` to be set before any UI components are created or shown.
  This can be useful to ensure a consistent appearance between the app-launcher
  splash and dialog UI with any later application UI, as well as to improve UI
  behavior on HiDPI displays using smarter Look & Feels such as
  [FlatLaf](https://www.formdev.com/flatlaf/).

* `scijava.app.java-root`: Directory containing "managed" installations of Java.
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
  the `scijava.app.java-platform` property must be set and match one of the keys
  indicated within the fetched remote resource.

* `scijava.app.java-platform`: Identifies the current platform, e.g.
  `linux-x64`, `macos-arm64`, `windows-x64`. Must match one of the keys in the
  file pointed to by `scijava.app.java-links` for a Java download to succeed.
  Typically set by the native launcher. If no matching entry is found, the
  detected platform string is included in the error message shown to the user.

* `scijava.app.java-version-minimum`: The minimum version of Java required by
  the application. It can be a standalone number like `11`, in which case it is
  treated as a major version, or a dot-separated sequence of digits, in which
  case version comparisons are done digit by digit (see `Versions.compare`).
  This value is used by `Java.check()` to warn the user if the running Java
  version is not good enough, and to determine whether the warning is framed as
  a hard requirement or a strong recommendation.

* `scijava.app.java-version-recommended`: The minimum version of Java the
  application would *prefer* to use. Same syntax as `java-version-minimum`.
  If the running version is at or above this value no warning is shown, even if
  it is below `java-version-minimum` — the minimum check only fires when both
  properties are set and the version is below both.

* `scijava.app.config-file`: Path to a key=value configuration file (typically
  the native launcher's config file, e.g. a Jaunch-compatible CFG file) where
  the `jvm-dir` entry is updated after a Java installation is selected or
  downloaded. This is how the app-launcher tells the native launcher which JVM
  to use on the next startup.

* `scijava.app.single-instance`: Set to `true` to enable single-instance mode.
  The first launch listens on a dynamically chosen TCP port and writes the port
  and a 128-bit random secret into an owner-readable-only lockfile. Subsequent
  launches read the lockfile, hand off their arguments to the running instance
  over the socket, and exit immediately. The lockfile name is derived from
  `scijava.app.name`.

* `scijava.app.unlock-modules`: Set to `true` to call
  `ReflectionUnlocker.unlockAll()` at startup, opening all Java modules to
  unnamed-module reflection. This is equivalent to passing `--add-opens` for
  every module but requires no knowledge of which modules need to be opened;
  only `--add-opens=java.base/java.lang=ALL-UNNAMED` need be passed.

* `scijava.app.debug`: Set to `true` to enable verbose debug logging to
  `stderr`. Debug mode can also be enabled via `scijava.log.level=debug` or by
  setting the `DEBUG_APP_LAUNCHER` environment variable.

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
