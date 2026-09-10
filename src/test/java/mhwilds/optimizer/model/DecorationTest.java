package mhwilds.optimizer.model;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DecorationTest {

  @Test
  void fitsInSlotOfEqualOrHigherLevel() {
    Decoration dec = new Decoration(1, "X", 2, Map.of(), SlotTarget.ARMOR);

    assertThat(dec.fitsInSlot(1)).isFalse();
    assertThat(dec.fitsInSlot(2)).isTrue();
    assertThat(dec.fitsInSlot(3)).isTrue();
  }

  @Test
  void levelMustBePositive() {
    assertThatThrownBy(() -> new Decoration(1, "X", 0, Map.of(), SlotTarget.ARMOR)).isInstanceOf(
      IllegalArgumentException.class
    );
  }
}
