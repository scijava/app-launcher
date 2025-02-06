package org.scijava.launcher;

public final class Shortcuts {

  private Shortcuts() { }

  public static void create() {
    // TODO: call correct helper method
  }

  private static void createWindows() {
    try {
      // CTR START HERE
      ShellLinkHelper.createLink("targetfile", "linkfile.lnk");
    }
    catch (ClassNotFoundException exc) {
      // CTR TODO
    }
  }

  private static void createLinux() {
    // CTR TODO
  }
}
