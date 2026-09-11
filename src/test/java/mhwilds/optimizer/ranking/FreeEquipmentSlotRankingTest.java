package mhwilds.optimizer.ranking;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import mhwilds.optimizer.model.*;
import org.junit.jupiter.api.Test;

class FreeEquipmentSlotRankingTest {

  private ArmorPiece piece(ArmorSlot kind, int[] slots) {
    return new ArmorPiece(0, "Set", kind, slots, Map.of(), 0, 0);
  }

  private Build buildWith(ArmorPiece[] armor, Weapon weapon, AmuletRank amulet) {
    return new Build(armor, List.of(), List.of(), amulet, weapon);
  }

  private Build fullBuild() {
    return buildWith(
      new ArmorPiece[] {
        piece(ArmorSlot.HEAD, new int[] { 3 }),
        piece(ArmorSlot.CHEST, new int[] {}),
        piece(ArmorSlot.ARMS, new int[] {}),
        piece(ArmorSlot.WAIST, new int[] {}),
        piece(ArmorSlot.LEGS, new int[] {}),
      },
      new Weapon(1, "bow", "Bow", 100, 0, new int[] { 3 }, Map.of(), List.of()),
      new AmuletRank(1, 1, "Charm", Map.of())
    );
  }

  private Build leanBuild() {
    return buildWith(
      new ArmorPiece[] { piece(ArmorSlot.HEAD, new int[] { 3 }), null, null, null, null },
      null,
      null
    );
  }

  @Test
  void countsOmittedEquipmentSlots() {
    assertThat(fullBuild().omittedEquipmentCount()).isZero();
    assertThat(leanBuild().omittedEquipmentCount()).isEqualTo(6);
  }

  @Test
  void emptyEquipmentSlotsAreRewardedByTheBonus() {
    assertThat(leanBuild().equipmentAwareScore(1000)).isEqualTo(
      leanBuild().freeSlotScore() + 6 * 1000
    );
  }

  @Test
  void leanBuildOutranksFullBuildOnceOmissionIsValued() {
    // Full build carries two free L3 slots (2000 pts); lean build omits 6 equipment slots
    // and, at bonus 1000 each, must outrank it.
    assertThat(fullBuild().freeSlotScore()).isEqualTo(2000);
    assertThat(leanBuild().freeSlotScore()).isEqualTo(1000);

    FreeEquipmentSlotRanking ranking = new FreeEquipmentSlotRanking(1000);
    Build lean = leanBuild();
    Build full = fullBuild();
    List<Build> sorted = List.of(full, lean).stream().sorted(ranking).toList();

    assertThat(sorted.get(0)).isSameAs(lean);
    assertThat(lean.equipmentAwareScore(1000)).isGreaterThan(full.equipmentAwareScore(1000));
  }

  @Test
  void equalEquipmentAwareScoresRankedByHigherMaxDefenseFirst() {
    Build low = new Build(
      new ArmorPiece[] {
        new ArmorPiece(0, "Set", ArmorSlot.HEAD, new int[] { 3 }, Map.of(), 0, 50),
        null,
        null,
        null,
        null,
      },
      List.of(),
      List.of(),
      null,
      null
    );
    Build high = new Build(
      new ArmorPiece[] {
        new ArmorPiece(0, "Set", ArmorSlot.HEAD, new int[] { 3 }, Map.of(), 0, 120),
        null,
        null,
        null,
        null,
      },
      List.of(),
      List.of(),
      null,
      null
    );

    assertThat(low.equipmentAwareScore(1000)).isEqualTo(high.equipmentAwareScore(1000));

    List<Build> out = List.of(low, high)
      .stream()
      .sorted(new FreeEquipmentSlotRanking(1000))
      .toList();
    assertThat(out.get(0)).isEqualTo(high);
  }

  @Test
  void factoryCreatesEquipmentSlotRanking() {
    RankingStrategy strategy = RankingFactory.create("free_equipment_slots", 1000);

    assertThat(strategy).isInstanceOf(FreeEquipmentSlotRanking.class);
  }

  @Test
  void factoryRecognizesEquipmentSlotRanking() {
    assertThat(RankingFactory.isEquipmentSlotRanking("free_equipment_slots")).isTrue();
    assertThat(RankingFactory.isEquipmentSlotRanking("free_slots")).isFalse();
  }
}
