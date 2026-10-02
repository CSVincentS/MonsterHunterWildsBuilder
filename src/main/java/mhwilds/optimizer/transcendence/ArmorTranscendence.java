package mhwilds.optimizer.transcendence;

import java.util.Arrays;

public record ArmorTranscendence(
  int rarity,
  int baseMaxLevel,
  int transcendedMaxLevel,
  int upgradedSlotCount
) {
  private static final int MAX_SLOT_LEVEL = 3;
  private static final int NEW_SLOT_LEVEL = 1;
  private static final int DEFENSE_PER_LEVEL = 2;

  public ArmorTranscendence {
    if (rarity < 1) {
      throw new IllegalArgumentException("rarity must be >= 1, got " + rarity);
    }

    if (baseMaxLevel < 1) {
      throw new IllegalArgumentException("baseMaxLevel must be >= 1, got " + baseMaxLevel);
    }

    if (transcendedMaxLevel < baseMaxLevel) {
      throw new IllegalArgumentException(
        "transcendedMaxLevel (" +
          transcendedMaxLevel +
          ") must be >= baseMaxLevel (" +
          baseMaxLevel +
          ") for rarity " +
          rarity
      );
    }

    if (upgradedSlotCount < 0) {
      throw new IllegalArgumentException(
        "upgradedSlotCount must be >= 0, got " + upgradedSlotCount
      );
    }
  }

  public boolean supported() {
    return transcendedMaxLevel > baseMaxLevel;
  }

  public int maxDefenseAt(int baseDefense, int level) {
    if (level < 1) {
      throw new IllegalArgumentException("level must be >= 1, got " + level);
    }

    return baseDefense + DEFENSE_PER_LEVEL * (level - 1);
  }

  public int transcendedDefense(int baseDefense) {
    return maxDefenseAt(baseDefense, transcendedMaxLevel);
  }

  public int[] transcendSlots(int[] slots) {
    int[] result = Arrays.copyOf(slots, slots.length);

    if (upgradedSlotCount <= 0) {
      return result;
    }

    boolean[] boosted = new boolean[result.length];
    int boostedCount = 0;

    while (boostedCount < upgradedSlotCount) {
      int target = -1;

      for (int i = 0; i < result.length; i++) {
        if (boosted[i] || result[i] >= MAX_SLOT_LEVEL) {
          continue;
        }

        if (target < 0 || result[i] > result[target]) {
          target = i;
        }
      }

      if (target < 0) {
        break;
      }

      result[target]++;
      boosted[target] = true;
      boostedCount++;
    }

    while (result.length < upgradedSlotCount) {
      result = Arrays.copyOf(result, result.length + 1);
      result[result.length - 1] = NEW_SLOT_LEVEL;
    }

    return result;
  }
}
