package mhwilds.optimizer.validity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Decoration;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.SlotAssignment;
import mhwilds.optimizer.model.SlotTarget;
import mhwilds.optimizer.model.Weapon;

public class BuildValidator {

  private final Map<Integer, Skill> skillMap;

  public BuildValidator(Map<Integer, Skill> skillMap) {
    this.skillMap = skillMap;
  }

  public List<String> validate(Build build) {
    List<String> errors = new ArrayList<>();

    if (build.weapon() == null) {
      errors.add("Build has no weapon assigned");
    }

    ArmorPiece[] pieces = build.armorPieces();
    ArmorSlot[] allSlots = ArmorSlot.values();

    for (int i = 0; i < allSlots.length; i++) {
      if (pieces[i] == null) {
        errors.add("Missing armor piece in " + allSlots[i].name().toLowerCase() + " slot");
      }
    }

    for (SlotAssignment sa : build.armorDecorations()) {
      ArmorPiece piece = pieces[sa.targetPiece().ordinal()];
      if (piece == null) {
        errors.add("Decoration assigned to missing armor piece in " + sa.targetPiece());
        continue;
      }

      int idx = sa.slotIndexWithinPiece();

      if (idx < 0 || idx >= piece.slots().length) {
        errors.add(
          "Slot index " +
            idx +
            " out of bounds for " +
            sa.targetPiece() +
            " piece (has " +
            piece.slots().length +
            " slots)"
        );
        continue;
      }
      int slotSize = piece.slots()[idx];
      Decoration dec = sa.decoration();

      if (dec.allowedOn() != SlotTarget.ARMOR) {
        errors.add(
          "Decoration '" +
            dec.name() +
            "' has allowed_on=" +
            dec.allowedOn() +
            " but is assigned to armor slot"
        );
      }

      if (!dec.fitsInSlot(slotSize)) {
        errors.add(
          "Decoration '" +
            dec.name() +
            "' level " +
            dec.level() +
            " does not fit in " +
            sa.targetPiece() +
            " slot " +
            idx +
            " of size " +
            slotSize
        );
      }
    }

    Weapon weapon = build.weapon();

    if (weapon != null) {
      for (SlotAssignment sa : build.weaponDecorations()) {
        int idx = sa.slotIndexWithinPiece();

        if (idx < 0 || idx >= weapon.slots().length) {
          errors.add(
            "Slot index " +
              idx +
              " out of bounds for weapon (has " +
              weapon.slots().length +
              " slots)"
          );
          continue;
        }

        int slotSize = weapon.slots()[idx];
        Decoration dec = sa.decoration();

        if (dec.allowedOn() != SlotTarget.WEAPON) {
          errors.add(
            "Decoration '" +
              dec.name() +
              "' has allowed_on=" +
              dec.allowedOn() +
              " but is assigned to weapon slot"
          );
        }

        if (!dec.fitsInSlot(slotSize)) {
          errors.add(
            "Decoration '" +
              dec.name() +
              "' level " +
              dec.level() +
              " does not fit in weapon slot " +
              idx +
              " of size " +
              slotSize
          );
        }
      }
    }

    Map<Integer, Integer> combined = build.combinedSkills();

    for (var entry : combined.entrySet()) {
      int skillId = entry.getKey();
      int total = entry.getValue();

      if (total < 0) {
        errors.add("Skill " + skillId + " has negative total: " + total);
      }
    }

    return errors;
  }
}
