package mhwilds.optimizer.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record AppraisedTalisman(
  String name,
  int rarity,
  Map<Integer, Integer> skills,
  int[] armorSlots,
  int[] weaponSlots
) {
  public static final int MIN_RARITY = 5;
  public static final int MAX_RARITY = 8;

  public static final int MAX_SKILLS = 3;
  public static final int MAX_ARMOR_SLOTS = 3;
  public static final int MAX_WEAPON_SLOTS = 3;
  public static final int MAX_SLOT_SIZE = SlotScore.MAX_SLOT_SIZE;

  public static final int FAMILY_ID_BASE = 2_000_000_000;

  public AppraisedTalisman {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }

    if (rarity < MIN_RARITY || rarity > MAX_RARITY) {
      throw new IllegalArgumentException(
        "rarity must be between " + MIN_RARITY + " and " + MAX_RARITY + ", got " + rarity
      );
    }

    if (skills == null || skills.isEmpty() || skills.size() > MAX_SKILLS) {
      throw new IllegalArgumentException("skills must hold 1 to " + MAX_SKILLS + " entries");
    }

    for (Map.Entry<Integer, Integer> entry : skills.entrySet()) {
      if (entry.getValue() == null || entry.getValue() < 1) {
        throw new IllegalArgumentException("skill levels must be >= 1");
      }
    }

    armorSlots = checkedSlots(armorSlots, MAX_ARMOR_SLOTS, "armorSlots");
    weaponSlots = checkedSlots(weaponSlots, MAX_WEAPON_SLOTS, "weaponSlots");

    skills = Map.copyOf(skills);
  }

  private static int[] checkedSlots(int[] slots, int maxCount, String what) {
    int[] cleaned = slots == null ? new int[0] : slots.clone();
    int kept = 0;

    for (int size : cleaned) {
      if (size == 0) {
        continue;
      }

      if (size < 0 || size > MAX_SLOT_SIZE) {
        throw new IllegalArgumentException(what + " entries must be 0.." + MAX_SLOT_SIZE);
      }

      cleaned[kept++] = size;
    }

    if (kept > maxCount) {
      throw new IllegalArgumentException(what + " holds at most " + maxCount + " slots");
    }

    int[] result = new int[kept];
    System.arraycopy(cleaned, 0, result, 0, kept);

    return result;
  }

  public static int familyIdForIndex(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("index must be >= 0, got " + index);
    }

    return FAMILY_ID_BASE + index;
  }

  public static boolean isAppraisedFamilyId(int familyGameId) {
    return familyGameId >= FAMILY_ID_BASE;
  }

  public AmuletRank toAmuletRank(int index) {
    Map<Integer, Integer> copy = new LinkedHashMap<>(skills);

    return new AmuletRank(
      familyIdForIndex(index),
      1,
      name,
      Map.copyOf(copy),
      rarity,
      armorSlots.clone(),
      weaponSlots.clone()
    );
  }
}
