package mhwilds.optimizer.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Build(
  ArmorPiece[] armorPieces,
  List<SlotAssignment> armorDecorations,
  List<SlotAssignment> weaponDecorations,
  AmuletRank amuletRank,
  Weapon weapon
) {
  public ArmorPiece armorPiece(ArmorSlot slot) {
    return armorPieces[slot.ordinal()];
  }

  public Map<Integer, Integer> combinedSkills() {
    Map<Integer, Integer> total = new HashMap<>();

    for (ArmorPiece piece : armorPieces) {
      if (piece == null) {
        continue;
      }

      for (var entry : piece.skills().entrySet()) {
        total.merge(entry.getKey(), entry.getValue(), Integer::sum);
      }
    }

    for (SlotAssignment sa : armorDecorations) {
      for (var entry : sa.decoration().skills().entrySet()) {
        total.merge(entry.getKey(), entry.getValue(), Integer::sum);
      }
    }

    for (SlotAssignment sa : weaponDecorations) {
      for (var entry : sa.decoration().skills().entrySet()) {
        total.merge(entry.getKey(), entry.getValue(), Integer::sum);
      }
    }

    if (amuletRank != null) {
      for (var entry : amuletRank.skills().entrySet()) {
        total.merge(entry.getKey(), entry.getValue(), Integer::sum);
      }
    }

    if (weapon != null) {
      for (var entry : weapon.skills().entrySet()) {
        total.merge(entry.getKey(), entry.getValue(), Integer::sum);
      }
    }

    return total;
  }

  public int totalSkillLevel(int skillId) {
    return combinedSkills().getOrDefault(skillId, 0);
  }

  public int totalDefense() {
    int total = 0;

    for (ArmorPiece piece : armorPieces) {
      if (piece != null) {
        total += piece.maxDefense();
      }
    }

    return total;
  }

  public Map<Integer, Integer> setBonusSkills() {
    Map<Integer, Integer> counts = new HashMap<>();
    Map<Integer, Map<Integer, Integer>> thresholds = new HashMap<>();

    for (ArmorPiece piece : armorPieces) {
      if (piece == null) {
        continue;
      }

      collectBonusTarget(counts, thresholds, piece.setBonusId(), piece.setBonusRanks());
      collectBonusTarget(counts, thresholds, piece.groupBonusId(), piece.groupBonusRanks());
    }

    Map<Integer, Integer> all = new HashMap<>();

    for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
      all.put(
        entry.getKey(),
        SetBonusActivation.levelFor(entry.getValue(), thresholds.get(entry.getKey()))
      );
    }

    return all;
  }

  public Map<Integer, Integer> activeSetBonusSkills() {
    Map<Integer, Integer> active = setBonusSkills();
    active.values().removeIf(v -> v <= 0);

    return active;
  }

  private static void collectBonusTarget(
    Map<Integer, Integer> counts,
    Map<Integer, Map<Integer, Integer>> thresholds,
    int bonusId,
    Map<Integer, Integer> ranks
  ) {
    if (bonusId == 0 || ranks == null || ranks.isEmpty()) {
      return;
    }

    counts.merge(bonusId, 1, Integer::sum);
    thresholds.putIfAbsent(bonusId, ranks);
  }

  public int totalSlotCount() {
    int total = 0;

    for (ArmorPiece p : armorPieces) {
      if (p != null) {
        total += p.slotCount();
      }
    }

    if (weapon != null) {
      total += weapon.slotCount();
    }

    return total;
  }

  public int freeSlotCount() {
    return totalSlotCount() - armorDecorations.size() - weaponDecorations.size();
  }

  public long freeSlotScore() {
    long score = 0;

    for (ArmorSlot slot : ArmorSlot.values()) {
      ArmorPiece piece = armorPiece(slot);

      if (piece == null) {
        continue;
      }

      boolean[] occupied = new boolean[piece.slotCount()];

      for (SlotAssignment sa : armorDecorations) {
        if (sa.targetPiece() == slot) {
          occupied[sa.slotIndexWithinPiece()] = true;
        }
      }

      for (int i = 0; i < piece.slots().length; i++) {
        if (!occupied[i]) {
          score += SlotScore.valueOf(piece.slots()[i]);
        }
      }
    }

    if (weapon != null) {
      boolean[] weaponOccupied = new boolean[weapon.slotCount()];

      for (SlotAssignment sa : weaponDecorations) {
        weaponOccupied[sa.slotIndexWithinPiece()] = true;
      }

      for (int i = 0; i < weapon.slots().length; i++) {
        if (!weaponOccupied[i]) {
          score += SlotScore.valueOf(weapon.slots()[i]);
        }
      }
    }

    return score;
  }

  public int omittedEquipmentCount() {
    int omitted = 0;

    for (ArmorPiece piece : armorPieces) {
      if (piece == null) {
        omitted++;
      }
    }

    if (weapon == null) {
      omitted++;
    }

    if (amuletRank == null) {
      omitted++;
    }

    return omitted;
  }

  public long equipmentAwareScore(long bonus) {
    return freeSlotScore() + bonus * omittedEquipmentCount();
  }
}
