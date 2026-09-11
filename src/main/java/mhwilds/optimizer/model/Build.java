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

  /**
   * Resolves set-bonus and group-bonus skills present on worn pieces, activation level 0 if not
   * met.
   */
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
      all.put(entry.getKey(), maxLevelAchieved(entry.getValue(), thresholds.get(entry.getKey())));
    }

    return all;
  }

  /**
   * Resolves set-bonus and group-bonus skills actually active (activation level > 0) on worn
   * pieces.
   */
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

  private static int maxLevelAchieved(int pieceCount, Map<Integer, Integer> ranks) {
    if (ranks == null) {
      return 0;
    }

    int best = 0;

    for (Map.Entry<Integer, Integer> rank : ranks.entrySet()) {
      if (pieceCount >= rank.getKey() && rank.getValue() > best) {
        best = rank.getValue();
      }
    }

    return best;
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
      ArmorPiece piece = armorPieces[slot.ordinal()];

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
          score += (long) Math.pow(10, piece.slots()[i]);
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
          score += (long) Math.pow(10, weapon.slots()[i]);
        }
      }
    }

    return score;
  }

  /** Number of equipment slots left empty: any armor piece, the weapon, or the amulet. */
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

  /**
   * Free-slot score with equipment omission rewarded: each empty equipment slot contributes {@code
   * bonus} points, so a lean build that needs fewer pieces can outrank a full build carrying free
   * level-3 decoration slots.
   */
  public long equipmentAwareScore(long bonus) {
    return freeSlotScore() + bonus * omittedEquipmentCount();
  }
}
