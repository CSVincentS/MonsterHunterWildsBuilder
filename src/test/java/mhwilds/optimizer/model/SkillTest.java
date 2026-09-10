package mhwilds.optimizer.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SkillTest {

  @Test
  void maxRankMustBePositive() {
    assertThatThrownBy(() -> new Skill(1, "Bad", 0)).isInstanceOf(IllegalArgumentException.class);
  }
}
