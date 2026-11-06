package mhwilds.optimizer.model;

import java.util.Map;

public record AmuletRank(
  int familyGameId,
  int rankLevel,
  String name,
  Map<Integer, Integer> skills,
  int rarity,
  int[] armorSlots,
  int[] weaponSlots
) {
  public AmuletRank {
    if (rankLevel < 1) {
      throw new IllegalArgumentException("rankLevel must be >= 1");
    }

    if (skills == null) {
      skills = Map.of();
    }

    if (armorSlots == null) {
      armorSlots = new int[0];
    }

    if (weaponSlots == null) {
      weaponSlots = new int[0];
    }
  }

  public AmuletRank(
    int familyGameId,
    int rankLevel,
    String name,
    Map<Integer, Integer> skills,
    int rarity
  ) {
    this(familyGameId, rankLevel, name, skills, rarity, new int[0], new int[0]);
  }

  public AmuletRank(int familyGameId, int rankLevel, String name, Map<Integer, Integer> skills) {
    this(familyGameId, rankLevel, name, skills, 0);
  }

  public int armorSlotCount() {
    return armorSlots.length;
  }

  public int weaponSlotCount() {
    return weaponSlots.length;
  }

  public long slotValue() {
    return SlotScore.score(armorSlots) + SlotScore.score(weaponSlots);
  }
}
