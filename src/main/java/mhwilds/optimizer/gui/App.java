package mhwilds.optimizer.gui;

import javax.swing.SwingUtilities;
import mhwilds.optimizer.cli.Main;
import mhwilds.optimizer.config.GearPoolConfig;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.loader.GameDataLoader;

public final class App {

  private App() {}

  public static void main(String[] args) {
    if (Main.containsHelp(args)) {
      System.out.println("Usage: mh-wilds-optimizer-gui [data-dir] [config-path]");

      return;
    }

    Main.CliArguments options;

    try {
      options = Main.parseArgs(args);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      System.exit(1);

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
}
