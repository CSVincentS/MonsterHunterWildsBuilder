package mhwilds.optimizer.gui;

import javax.swing.SwingUtilities;
import mhwilds.optimizer.cli.Main;
import mhwilds.optimizer.config.GearPoolConfig;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.loader.GameDataLoader;

/** GUI entry point: parse args, load data + config, open the window on the EDT. */
public final class App {

  private App() {}

  public static void main(String[] args) {
    Main.CliArguments options;

    try {
      options = Main.parseArgs(args);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      System.exit(1);

      return;
    }

    if (isHelp(args)) {
      System.out.println("Usage: mh-wilds-optimizer-gui [data-dir] [config-path]");

      return;
    }

    try {
      GameData data = new GameDataLoader(options.dataDir()).load();

      GearPoolConfig config = GearPoolConfig.load(options.configPath());

      SwingUtilities.invokeLater(() -> new OptimizerWindow(data, config).setVisible(true));
    } catch (RuntimeException e) {
      System.err.println("Failed to start GUI: " + e.getMessage());
      System.exit(1);
    }
  }

  private static boolean isHelp(String[] args) {
    for (String arg : args) {
      if (arg.equals("--help") || arg.equals("-h")) {
        return true;
      }
    }

    return false;
  }
}
