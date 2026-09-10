package mhwilds.optimizer.model;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ArmorPieceTest {

  @Test
  void kindCannotBeNull() {
    assertThatThrownBy(() ->
      new ArmorPiece(1, "X", null, new int[] {}, Map.of(), 0, 0)
    ).isInstanceOf(NullPointerException.class);
  }

  @Test
  void slotsCannotBeNull() {
    assertThatThrownBy(() ->
      new ArmorPiece(1, "X", ArmorSlot.HEAD, null, Map.of(), 0, 0)
    ).isInstanceOf(NullPointerException.class);
  }
}
