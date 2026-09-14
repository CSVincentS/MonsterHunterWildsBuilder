package mhwilds.optimizer.cli;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MainCliArgumentsTest {

  @Test
  void defaultsWhenNoArgs() {
    Main.CliArguments o = Main.parseArgs(new String[] {});

    assertThat(o.dataDir()).isEqualTo("data");
    assertThat(o.configPath()).isEqualTo("src/main/resources/default-config.json");
    assertThat(o.topN()).isEqualTo(1000);
    assertThat(o.showTopN()).isNull();
    assertThat(o.ranking()).isNull();
    assertThat(o.outputPath()).isNull();
  }

  @Test
  void explicitDefaultDataDirStillLeavesConfigPathSettable() {
    Main.CliArguments o = Main.parseArgs(new String[] { "data", "my-config.json" });

    assertThat(o.dataDir()).isEqualTo("data");
    assertThat(o.configPath()).isEqualTo("my-config.json");
  }

  @Test
  void customDataDirAndConfig() {
    Main.CliArguments o = Main.parseArgs(new String[] { "mydata", "myconfig.json" });

    assertThat(o.dataDir()).isEqualTo("mydata");
    assertThat(o.configPath()).isEqualTo("myconfig.json");
  }

  @Test
  void flagsAndPositionalsCombine() {
    Main.CliArguments o = Main.parseArgs(new String[] {
      "data",
      "cfg.json",
      "--top",
      "3",
      "--show",
      "1",
      "--ranking",
      "free_slots",
      "--output",
      "out.txt",
    });

    assertThat(o.dataDir()).isEqualTo("data");
    assertThat(o.configPath()).isEqualTo("cfg.json");
    assertThat(o.topN()).isEqualTo(3);
    assertThat(o.showTopN()).isEqualTo(1);
    assertThat(o.ranking()).isEqualTo("free_slots");
    assertThat(o.outputPath()).isEqualTo("out.txt");
  }

  @Test
  void zeroTopRejected() {
    assertThatThrownBy(() -> Main.parseArgs(new String[] { "--top", "0" }))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("--top must be >= 1, got 0");
  }

  @Test
  void zeroShowRejected() {
    assertThatThrownBy(() -> Main.parseArgs(new String[] { "--show", "0" }))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("--show must be >= 1, got 0");
  }

  @Test
  void nonIntegerShowRejected() {
    assertThatThrownBy(() -> Main.parseArgs(new String[] { "--show", "many" }))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("--show requires an integer, got many");
  }

  @Test
  void thirdPositionalRejected() {
    assertThatThrownBy(() -> Main.parseArgs(new String[] { "a", "b", "c" }))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Unexpected argument: c");
  }

  @Test
  void unknownOptionRejected() {
    assertThatThrownBy(() -> Main.parseArgs(new String[] { "--bogus" }))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Unknown option: --bogus");
  }

  @Test
  void missingTopValueRejected() {
    assertThatThrownBy(() -> Main.parseArgs(new String[] { "--top" }))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("--top requires a value");
  }

  @Test
  void nonIntegerTopValueRejected() {
    assertThatThrownBy(() -> Main.parseArgs(new String[] { "--top", "many" }))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("--top requires an integer, got many");
  }
}
