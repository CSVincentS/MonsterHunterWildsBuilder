package mhwilds.optimizer.model;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AmuletRankTest {

  @Test
  void rankLevelMustBePositive() {
    assertThatThrownBy(() -> new AmuletRank(1, 0, "X", Map.of())).isInstanceOf(
      IllegalArgumentException.class
    );
  }
}
