package mhwilds.optimizer.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import org.junit.jupiter.api.Test;

class OptimizerWindowHeadlessTest {

  @Test
  void windowConstructsOnDisplayCapableEnvironment() {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");

    mhwilds.optimizer.loader.GameData data = new mhwilds.optimizer.loader.GameDataLoader(
      "data"
    ).load();
    mhwilds.optimizer.config.GearPoolConfig config = mhwilds.optimizer.config.GearPoolConfig.load(
      "src/main/resources/default-config.json"
    );
    OptimizerWindow window = new OptimizerWindow(data, config);

    assertThat(window.getTitle()).isEqualTo("MH Wilds Loadout Optimizer");
    window.dispose();
  }
}
