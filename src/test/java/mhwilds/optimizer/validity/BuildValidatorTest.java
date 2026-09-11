package mhwilds.optimizer.validity;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BuildValidatorTest {

  private Map<Integer, Skill> skillMap;
  private BuildValidator validator;

  @BeforeEach
  void setup() {
    skillMap = Map.of(100, new Skill(100, "TestSkillA", 5), 200, new Skill(200, "TestSkillB", 3));
    validator = new BuildValidator(skillMap);
  }

  private ArmorPiece piece(ArmorSlot kind, int[] slots, Map<Integer, Integer> skills) {
    return new ArmorPiece(0, "Set", kind, slots, skills, 0, 0);
  }

  private ArmorPiece[] fivePieces(ArmorSlot skip) {
    ArmorSlot[] slots = {
      ArmorSlot.HEAD,
      ArmorSlot.CHEST,
      ArmorSlot.ARMS,
      ArmorSlot.WAIST,
      ArmorSlot.LEGS,
    };
    ArmorPiece[] result = new ArmorPiece[5];

    for (int i = 0; i < 5; i++) {
      if (slots[i] == skip) {
        result[i] = null;
      } else {
        result[i] = piece(slots[i], new int[] {}, Map.of());
      }
    }

    return result;
  }

  @Test
  void validBuildPasses() {
    ArmorPiece[] pieces = fivePieces(null);
    Weapon weapon = new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of());
    Build build = new Build(pieces, List.of(), List.of(), null, weapon);

    List<String> errors = validator.validate(build);
    assertThat(errors).isEmpty();
  }

  @Test
  void missingArmorSlotFails() {
    ArmorPiece[] pieces = fivePieces(ArmorSlot.HEAD);
    Weapon weapon = new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of());
    Build build = new Build(pieces, List.of(), List.of(), null, weapon);

    List<String> errors = validator.validate(build);
    assertThat(errors).anyMatch(e -> e.contains("head"));
  }

  @Test
  void decorationExceedingSlotSizeFails() {
    ArmorPiece head = piece(ArmorSlot.HEAD, new int[] { 1 }, Map.of());
    ArmorPiece[] pieces = fivePieces(null);
    pieces[0] = head;
    Decoration bigDec = new Decoration(1, "Big", 3, Map.of(), SlotTarget.ARMOR);
    SlotAssignment sa = new SlotAssignment(bigDec, ArmorSlot.HEAD, 0);
    Weapon weapon = new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of());
    Build build = new Build(pieces, List.of(sa), List.of(), null, weapon);

    List<String> errors = validator.validate(build);
    assertThat(errors).anyMatch(e -> e.contains("slot"));
  }

  @Test
  void weaponDecorationOnArmorSlotFails() {
    ArmorPiece head = piece(ArmorSlot.HEAD, new int[] { 3 }, Map.of());
    ArmorPiece[] pieces = fivePieces(null);
    pieces[0] = head;
    Decoration weaponDec = new Decoration(1, "Wep", 1, Map.of(), SlotTarget.WEAPON);
    SlotAssignment sa = new SlotAssignment(weaponDec, ArmorSlot.HEAD, 0);
    Weapon weapon = new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of());
    Build build = new Build(pieces, List.of(sa), List.of(), null, weapon);

    List<String> errors = validator.validate(build);
    assertThat(errors).anyMatch(e -> e.contains("allowed_on") || e.contains("weapon"));
  }

  @Test
  void skillOverMaxRankIsLegal() {
    ArmorPiece head = piece(ArmorSlot.HEAD, new int[] {}, Map.of(100, 6));
    ArmorPiece[] pieces = fivePieces(null);
    pieces[0] = head;
    Weapon weapon = new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of());
    Build build = new Build(pieces, List.of(), List.of(), null, weapon);

    List<String> errors = validator.validate(build);
    assertThat(errors).isEmpty();
  }

  @Test
  void noWeaponFails() {
    ArmorPiece[] pieces = fivePieces(null);
    Build build = new Build(pieces, List.of(), List.of(), null, null);

    List<String> errors = validator.validate(build);
    assertThat(errors).anyMatch(e -> e.contains("weapon"));
  }
}
