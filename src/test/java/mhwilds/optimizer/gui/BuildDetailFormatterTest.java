package mhwilds.optimizer.gui;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.*;
import org.junit.jupiter.api.Test;

class BuildDetailFormatterTest {

  private ArmorPiece piece(ArmorSlot slot, String setName, Map<Integer, Integer> skills) {
    return new ArmorPiece(1, setName, slot, new int[] { 2 }, skills, 10, 20, 6);
  }

  @Test
  void decorationsSummaryCollapsesCountsAndTargets() {
    Decoration atk = new Decoration(10, "Attack Jewel [1]", 1, Map.of(100, 1), SlotTarget.ARMOR, 5);
    Decoration phys = new Decoration(
      11,
      "Physique Jewel [1]",
      1,
      Map.of(101, 1),
      SlotTarget.ARMOR,
      5
    );
    Decoration mig = new Decoration(12, "Mighty Jewel [2]", 1, Map.of(102, 2), SlotTarget.ARMOR, 5);
    Decoration weapon = new Decoration(
      13,
      "Draw Jewel [1]",
      1,
      Map.of(103, 1),
      SlotTarget.WEAPON,
      5
    );

    ArmorPiece head = piece(ArmorSlot.HEAD, "Orion α", Map.of());
    Build build = new Build(
      new ArmorPiece[] { head, null, null, null, null },
      List.of(
        new SlotAssignment(atk, ArmorSlot.HEAD, 0),
        new SlotAssignment(atk, ArmorSlot.HEAD, 1),
        new SlotAssignment(phys, ArmorSlot.HEAD, 2),
        new SlotAssignment(mig, ArmorSlot.ARMS, 0)
      ),
      List.of(new SlotAssignment(weapon, null, 0)),
      new AmuletRank(1, 1, "Charm", Map.of()),
      new Weapon(2, "bow", "Bow", 100, 0, new int[] { 2 }, Map.of(), List.of(), 7)
    );

    assertThat(BuildDetailFormatter.decorationsSummary(build)).isEqualTo(
      "Attack Jewel [1]→head ×2, Draw Jewel [1]→weapon, Mighty Jewel [2]→arms, Physique Jewel [1]→head"
    );
  }

  @Test
  void decorationsSummaryNone() {
    Build empty = new Build(new ArmorPiece[5], List.of(), List.of(), null, null);

    assertThat(BuildDetailFormatter.decorationsSummary(empty)).isEqualTo("(none)");
  }

  @Test
  void detailTextShowsPerPieceSlotDetail() {
    Decoration atk = new Decoration(10, "Attack Jewel [3]", 3, Map.of(100, 1), SlotTarget.ARMOR, 5);
    Decoration draw = new Decoration(13, "Draw Jewel [1]", 1, Map.of(200, 1), SlotTarget.WEAPON, 5);
    ArmorPiece head = new ArmorPiece(
      1,
      "Orion α",
      ArmorSlot.HEAD,
      new int[] { 3, 2 },
      Map.of(100, 1),
      10,
      20,
      6
    );
    ArmorPiece chest = new ArmorPiece(
      2,
      "Artian β",
      ArmorSlot.CHEST,
      new int[] { 1 },
      Map.of(100, 1),
      10,
      20,
      6
    );
    Build build = new Build(
      new ArmorPiece[] { head, chest, null, null, null },
      List.of(new SlotAssignment(atk, ArmorSlot.HEAD, 0)),
      List.of(new SlotAssignment(draw, null, 0)),
      new AmuletRank(1, 1, "Charm I", Map.of(100, 1)),
      new Weapon(
        2,
        "bow",
        "Calamitous Angel",
        100,
        0,
        new int[] { 2, 1 },
        Map.of(200, 2),
        List.of(),
        7
      )
    );

    String text = BuildDetailFormatter.detailText(
      build,
      Map.of(100, new Skill(100, "Attack Boost", 5), 200, new Skill(200, "Zodiac", 5)),
      Map.of()
    );

    // Armor pieces show per-slot detail: L<level> deco_name or L<level> —
    assertThat(text).contains("L3 Attack Jewel [3]  L2 —");
    assertThat(text).contains("chest  Artian β");
    assertThat(text).contains("L1 —");
    // Null pieces are skipped
    assertThat(text).doesNotContain("arms");
    assertThat(text).doesNotContain("waist");
    assertThat(text).doesNotContain("legs");
    // Weapon shows per-slot detail
    assertThat(text).contains("Calamitous Angel");
    assertThat(text).contains("L2 Draw Jewel [1]  L1 —");
    assertThat(text).contains("Weapon:");
    // No aggregate decoration summary line
    assertThat(text).doesNotContain("→");
    // Skills (100: 1+1+1+1=4, 200: 1+2=3) sorted highest first, capped at max
    assertThat(text).contains("Attack Boost          4/5");
    assertThat(text).contains("Zodiac                3/5");
    // Amulet
    assertThat(text).contains("Amulet: Charm I");
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
  void detailTextShowsSetSkillsSection() {
    Map<Integer, Integer> goreRanks = Map.of(2, 1, 4, 2);
    Build build = new Build(
      new ArmorPiece[] {
        bonusedPiece("Gore α", ArmorSlot.HEAD, 722735744, goreRanks, 0, Map.of()),
        new ArmorPiece(
          0,
          "Gore β",
          ArmorSlot.CHEST,
          new int[] {},
          Map.of(722735744, 1),
          0,
          0,
          0,
          722735744,
          goreRanks,
          0,
          Map.of()
        ),
        piece(ArmorSlot.ARMS, "Set", Map.of()),
        piece(ArmorSlot.WAIST, "Set", Map.of()),
        piece(ArmorSlot.LEGS, "Set", Map.of()),
      },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    String text = BuildDetailFormatter.detailText(
      build,
      Map.of(722735744, new Skill(722735744, "Gore Magala's Tyranny", 2)),
      Map.of(722735744, goreRanks)
    );

    assertThat(text).contains("Set skills:");
    assertThat(text).contains("Gore Magala's Tyranny");
    assertThat(text).contains("1/2");
    assertThat(text).contains("\nSet skills:\n");
    assertThat(text).containsSubsequence("Skills:\n  (none)", "1/2");
  }

  @Test
  void detailTextOmitsSetSkillsWhenNoneActive() {
    Build build = new Build(
      new ArmorPiece[] {
        piece(ArmorSlot.HEAD, "Orion α", Map.of()),
        piece(ArmorSlot.CHEST, "Orion α", Map.of()),
        piece(ArmorSlot.ARMS, "Orion α", Map.of()),
        piece(ArmorSlot.WAIST, "Orion α", Map.of()),
        piece(ArmorSlot.LEGS, "Orion α", Map.of()),
      },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    String text = BuildDetailFormatter.detailText(build, Map.of(), Map.of());

    assertThat(text).doesNotContain("Set skills:");
  }

  @Test
  void detailTextHidesSetSkillsFromSkillsListAndShowsInactiveAtZero() {
    Map<Integer, Integer> goreRanks = Map.of(2, 1, 4, 2);
    ArmorPiece goreHead = new ArmorPiece(
      0,
      "Gore α",
      ArmorSlot.HEAD,
      new int[] {},
      Map.of(722735744, 1),
      0,
      0,
      0,
      722735744,
      goreRanks,
      0,
      Map.of()
    );
    Build build = new Build(
      new ArmorPiece[] {
        goreHead,
        piece(ArmorSlot.CHEST, "Set", Map.of()),
        piece(ArmorSlot.ARMS, "Set", Map.of()),
        piece(ArmorSlot.WAIST, "Set", Map.of()),
        piece(ArmorSlot.LEGS, "Set", Map.of()),
      },
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    String text = BuildDetailFormatter.detailText(
      build,
      Map.of(722735744, new Skill(722735744, "Gore Magala's Tyranny", 2)),
      Map.of(722735744, goreRanks)
    );

    assertThat(text).containsSubsequence("Skills:\n  (none)", "Set skills:");
    assertThat(text).contains("Gore Magala's Tyranny");
    assertThat(text).contains("0/2");
    assertThat(text).doesNotContain("1/2");
  }
}
