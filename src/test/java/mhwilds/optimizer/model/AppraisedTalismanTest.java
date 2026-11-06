package mhwilds.optimizer.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AppraisedTalismanTest {

  @Test
  void validTalismanKeepsSlotsAndSkills() {
    AppraisedTalisman talisman = new AppraisedTalisman(
      "Golden Charm",
      8,
      Map.of(100, 2, 200, 1),
      new int[] { 1, 1 },
      new int[] { 1 }
    );

    assertThat(talisman.armorSlots()).containsExactly(1, 1);
    assertThat(talisman.weaponSlots()).containsExactly(1);
    assertThat(talisman.skills()).containsExactlyInAnyOrderEntriesOf(Map.of(100, 2, 200, 1));
  }

  @Test
  void zeroSlotsAreDropped() {
    AppraisedTalisman talisman = new AppraisedTalisman(
      "Charm",
      5,
      Map.of(100, 1),
      new int[] { 3, 0, 0 },
      new int[] { 0 }
    );

    assertThat(talisman.armorSlots()).containsExactly(3);
    assertThat(talisman.weaponSlots()).isEmpty();
  }

  @Test
  void rarityOutsideFiveToEightRejected() {
    assertThatThrownBy(() ->
      new AppraisedTalisman("Charm", 4, Map.of(100, 1), new int[0], new int[0])
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankNameAndEmptySkillsRejected() {
    assertThatThrownBy(() ->
      new AppraisedTalisman("  ", 8, Map.of(100, 1), new int[0], new int[0])
    ).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
      new AppraisedTalisman("Charm", 8, Map.of(), new int[0], new int[0])
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void moreThanThreeSkillsRejected() {
    assertThatThrownBy(() ->
      new AppraisedTalisman("Charm", 8, Map.of(1, 1, 2, 1, 3, 1, 4, 1), new int[0], new int[0])
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toAmuletRankUsesSyntheticFamilyId() {
    AppraisedTalisman talisman = new AppraisedTalisman(
      "Charm",
      7,
      Map.of(100, 3),
      new int[] { 2 },
      new int[0]
    );

    AmuletRank rank = talisman.toAmuletRank(2);

    assertThat(rank.familyGameId()).isEqualTo(AppraisedTalisman.familyIdForIndex(2));
    assertThat(AppraisedTalisman.isAppraisedFamilyId(rank.familyGameId())).isTrue();
    assertThat(AppraisedTalisman.isAppraisedFamilyId(123)).isFalse();
    assertThat(rank.name()).isEqualTo("Charm");
    assertThat(rank.rarity()).isEqualTo(7);
    assertThat(rank.skills()).containsExactlyInAnyOrderEntriesOf(Map.of(100, 3));
    assertThat(rank.armorSlots()).containsExactly(2);
    assertThat(rank.weaponSlots()).isEmpty();
  }
}
