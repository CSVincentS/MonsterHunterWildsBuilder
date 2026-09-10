package mhwilds.optimizer.model;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeaponTest {

  @Test
  void weaponSlotCount() {
    Weapon w = new Weapon(
      1,
      "great-sword",
      "X",
      100,
      0,
      new int[] { 3, 2, 1 },
      Map.of(),
      List.of()
    );

    assertThat(w.slotCount()).isEqualTo(3);
    assertThat(w.extraFields()).isEmpty();
    assertThat(w.rarity()).isZero();
  }

  @Test
  void nullsDefaultToEmpty() {
    Weapon w = new Weapon(1, "bow", "X", 100, 0, null, null, null, null, 5);

    assertThat(w.slots()).isEmpty();
    assertThat(w.skills()).isEmpty();
    assertThat(w.coatings()).isEmpty();
    assertThat(w.extraFields()).isEmpty();
  }

  @Test
  void blankKindRejected() {
    assertThatThrownBy(() ->
      new Weapon(1, "", "X", 100, 0, new int[] {}, Map.of(), List.of())
    ).isInstanceOf(IllegalArgumentException.class);
  }
}
