package mhwilds.optimizer.model;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildTest {

  private ArmorPiece makePiece(ArmorSlot kind, int[] slots, Map<Integer, Integer> skills) {
    return new ArmorPiece(0, "Set", kind, slots, skills, 0, 0);
  }

  @Test
  void combinedSkillsSumsAllSources() {
    ArmorPiece head = makePiece(ArmorSlot.HEAD, new int[] {}, Map.of(100, 2));
    ArmorPiece chest = makePiece(ArmorSlot.CHEST, new int[] {}, Map.of(100, 1, 200, 3));
    ArmorPiece arms = makePiece(ArmorSlot.ARMS, new int[] {}, Map.of());
    ArmorPiece waist = makePiece(ArmorSlot.WAIST, new int[] {}, Map.of());
    ArmorPiece legs = makePiece(ArmorSlot.LEGS, new int[] {}, Map.of(200, 1));

    AmuletRank amulet = new AmuletRank(1, 1, "Charm", Map.of(100, 1));
    Weapon weapon = new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(300, 1), List.of());

    Build build = new Build(
      new ArmorPiece[] { head, chest, arms, waist, legs },
      List.of(),
      List.of(),
      amulet,
      weapon
    );

    Map<Integer, Integer> combined = build.combinedSkills();
    assertThat(combined).containsEntry(100, 4); // 2+1+1
    assertThat(combined).containsEntry(200, 4); // 3+1
    assertThat(combined).containsEntry(300, 1); // bow
  }

  @Test
  void freeSlotScoreWeightsHigherSlotsMore() {
    ArmorPiece[] noSlots = new ArmorPiece[] {
      makePiece(ArmorSlot.HEAD, new int[] {}, Map.of()),
      makePiece(ArmorSlot.CHEST, new int[] {}, Map.of()),
      makePiece(ArmorSlot.ARMS, new int[] {}, Map.of()),
      makePiece(ArmorSlot.WAIST, new int[] {}, Map.of()),
      makePiece(ArmorSlot.LEGS, new int[] {}, Map.of()),
    };
    ArmorPiece head3 = makePiece(ArmorSlot.HEAD, new int[] { 3 }, Map.of());
    Build buildA = new Build(
      new ArmorPiece[] { head3, noSlots[1], noSlots[2], noSlots[3], noSlots[4] },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );
    ArmorPiece head1 = makePiece(ArmorSlot.HEAD, new int[] { 1 }, Map.of());
    Build buildB = new Build(
      new ArmorPiece[] { head1, noSlots[1], noSlots[2], noSlots[3], noSlots[4] },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    assertThat(buildA.freeSlotScore()).isGreaterThan(buildB.freeSlotScore());
  }

  @Test
  void totalDefenseSumsMaxDefenseAcrossPieces() {
    ArmorPiece head = new ArmorPiece(1, "SetA", ArmorSlot.HEAD, new int[] {}, Map.of(), 10, 50);
    ArmorPiece chest = new ArmorPiece(2, "SetB", ArmorSlot.CHEST, new int[] {}, Map.of(), 20, 70);
    ArmorPiece arms = new ArmorPiece(3, "SetC", ArmorSlot.ARMS, new int[] {}, Map.of(), 0, 0);
    ArmorPiece waist = new ArmorPiece(4, "SetD", ArmorSlot.WAIST, new int[] {}, Map.of(), 15, 60);
    ArmorPiece legs = new ArmorPiece(5, "SetE", ArmorSlot.LEGS, new int[] {}, Map.of(), 25, 90);
    Build build = new Build(
      new ArmorPiece[] { head, chest, arms, waist, legs },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    assertThat(build.totalDefense()).isEqualTo(50 + 70 + 0 + 60 + 90);
  }

  @Test
  void decorationOccupiesSlot() {
    ArmorPiece head = makePiece(ArmorSlot.HEAD, new int[] { 3, 1 }, Map.of());
    Decoration dec = new Decoration(1, "Jewel", 1, Map.of(100, 1), SlotTarget.ARMOR);
    SlotAssignment assignment = new SlotAssignment(dec, ArmorSlot.HEAD, 0);

    Build build = new Build(
      new ArmorPiece[] {
        head,
        makePiece(ArmorSlot.CHEST, new int[] {}, Map.of()),
        makePiece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        makePiece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        makePiece(ArmorSlot.LEGS, new int[] {}, Map.of()),
      },
      List.of(assignment),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    assertThat(build.freeSlotCount()).isEqualTo(1);
    assertThat(build.combinedSkills()).containsEntry(100, 1);
  }

  private ArmorPiece bonusedPiece(
    String setName,
    ArmorSlot kind,
    int setBonusId,
    Map<Integer, Integer> setRanks,
    int groupBonusId,
    Map<Integer, Integer> groupRanks
  ) {
    return new ArmorPiece(
      0,
      setName,
      kind,
      new int[] {},
      Map.of(),
      0,
      0,
      0,
      setBonusId,
      setRanks,
      groupBonusId,
      groupRanks
    );
  }

  @Test
  void setBonusActivatedByPieceCount() {
    Map<Integer, Integer> goreRanks = Map.of(2, 1, 4, 2);
    Build twoPieces = new Build(
      new ArmorPiece[] {
        bonusedPiece("Set", ArmorSlot.HEAD, 722735744, goreRanks, 0, Map.of()),
        bonusedPiece("Set", ArmorSlot.CHEST, 722735744, goreRanks, 0, Map.of()),
        makePiece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        makePiece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        makePiece(ArmorSlot.LEGS, new int[] {}, Map.of()),
      },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    assertThat(twoPieces.activeSetBonusSkills()).containsEntry(722735744, 1);

    Build fourPieces = new Build(
      new ArmorPiece[] {
        bonusedPiece("Set", ArmorSlot.HEAD, 722735744, goreRanks, 0, Map.of()),
        bonusedPiece("Set", ArmorSlot.CHEST, 722735744, goreRanks, 0, Map.of()),
        bonusedPiece("Set", ArmorSlot.ARMS, 722735744, goreRanks, 0, Map.of()),
        bonusedPiece("Set", ArmorSlot.WAIST, 722735744, goreRanks, 0, Map.of()),
        makePiece(ArmorSlot.LEGS, new int[] {}, Map.of()),
      },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    assertThat(fourPieces.activeSetBonusSkills()).containsEntry(722735744, 2);
  }

  @Test
  void setBonusCombinesDifferentSetsSharingBonusId() {
    Map<Integer, Integer> goreRanks = Map.of(2, 1, 4, 2);
    Build mixed = new Build(
      new ArmorPiece[] {
        bonusedPiece("Gore α", ArmorSlot.HEAD, 722735744, goreRanks, 0, Map.of()),
        bonusedPiece("Gore β", ArmorSlot.CHEST, 722735744, goreRanks, 0, Map.of()),
        makePiece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        makePiece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        makePiece(ArmorSlot.LEGS, new int[] {}, Map.of()),
      },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    assertThat(mixed.activeSetBonusSkills()).containsEntry(722735744, 1);
  }

  @Test
  void groupBonusActivatedByPieceCount() {
    Map<Integer, Integer> peltRanks = Map.of(3, 1);
    Build threePieces = new Build(
      new ArmorPiece[] {
        bonusedPiece("Set", ArmorSlot.HEAD, 0, Map.of(), 1998066176, peltRanks),
        bonusedPiece("Set", ArmorSlot.CHEST, 0, Map.of(), 1998066176, peltRanks),
        bonusedPiece("Set", ArmorSlot.ARMS, 0, Map.of(), 1998066176, peltRanks),
        makePiece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        makePiece(ArmorSlot.LEGS, new int[] {}, Map.of()),
      },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    assertThat(threePieces.activeSetBonusSkills()).containsEntry(1998066176, 1);
  }

  @Test
  void inactiveBonusesOmitted() {
    Map<Integer, Integer> goreRanks = Map.of(2, 1, 4, 2);
    Build singlePiece = new Build(
      new ArmorPiece[] {
        bonusedPiece("Set", ArmorSlot.HEAD, 722735744, goreRanks, 0, Map.of()),
        makePiece(ArmorSlot.CHEST, new int[] {}, Map.of()),
        makePiece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        makePiece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        makePiece(ArmorSlot.LEGS, new int[] {}, Map.of()),
      },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    assertThat(singlePiece.activeSetBonusSkills()).doesNotContainKey(722735744);
    assertThat(singlePiece.activeSetBonusSkills()).isEmpty();
  }

  @Test
  void setBonusSkillsIncludesUnactivatedBonuses() {
    Map<Integer, Integer> goreRanks = Map.of(2, 1, 4, 2);
    Build singlePiece = new Build(
      new ArmorPiece[] {
        bonusedPiece("Set", ArmorSlot.HEAD, 722735744, goreRanks, 0, Map.of()),
        makePiece(ArmorSlot.CHEST, new int[] {}, Map.of()),
        makePiece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        makePiece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        makePiece(ArmorSlot.LEGS, new int[] {}, Map.of()),
      },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    assertThat(singlePiece.setBonusSkills()).containsExactly(Map.entry(722735744, 0));
    assertThat(singlePiece.activeSetBonusSkills()).isEmpty();
  }

  @Test
  void setBonusSkillsNotIncludedInCombinedSkills() {
    Map<Integer, Integer> goreRanks = Map.of(2, 1, 4, 2);
    Build build = new Build(
      new ArmorPiece[] {
        bonusedPiece("Set", ArmorSlot.HEAD, 722735744, goreRanks, 0, Map.of()),
        bonusedPiece("Set", ArmorSlot.CHEST, 722735744, goreRanks, 0, Map.of()),
        makePiece(ArmorSlot.ARMS, new int[] {}, Map.of()),
        makePiece(ArmorSlot.WAIST, new int[] {}, Map.of()),
        makePiece(ArmorSlot.LEGS, new int[] {}, Map.of()),
      },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    assertThat(build.combinedSkills()).doesNotContainKey(722735744);
  }
}
