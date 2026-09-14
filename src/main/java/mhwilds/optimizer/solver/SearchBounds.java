package mhwilds.optimizer.solver;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Decoration;
import mhwilds.optimizer.model.SlotScore;
import mhwilds.optimizer.solver.GearOptions.AmuletOpt;
import mhwilds.optimizer.solver.GearOptions.WeaponOpt;

final class SearchBounds {

  private static final int ARMOR_SLOT_COUNT = ArmorSlot.values().length;
  private static final int SLOT_SIZES = SlotScore.MAX_SLOT_SIZE;

  private final int n;
  private final int[] required;
  private final Map<Integer, Integer> skillPos;
  private final List<Integer> nonSetIds;

  private long weaponMaxSlotValue;
  private final long[] bestAmu;
  private final long[] bestWeaponInnate;
  private final long[] bestArmorDeco;
  private final long[] bestWeaponDeco;
  private final int[] armorDecoBestTotal;
  private final int[] weaponDecoBestTotal;
  private final int[][] armorDecoBest;
  private final int[][] weaponDecoBest;
  private final int[] suffixSlots;
  private final int[][] suffixSkill;

  SearchBounds(int n, int[] required, Map<Integer, Integer> skillPos, List<Integer> nonSetIds) {
    this.n = n;
    this.required = required;
    this.skillPos = skillPos;
    this.nonSetIds = nonSetIds;
    this.bestAmu = new long[n];
    this.bestWeaponInnate = new long[n];
    this.bestArmorDeco = new long[n];
    this.bestWeaponDeco = new long[n];
    this.armorDecoBestTotal = new int[SLOT_SIZES];
    this.weaponDecoBestTotal = new int[SLOT_SIZES];
    this.armorDecoBest = new int[SLOT_SIZES][n];
    this.weaponDecoBest = new int[SLOT_SIZES][n];
    this.suffixSlots = new int[ARMOR_SLOT_COUNT + 1];
    this.suffixSkill = new int[ARMOR_SLOT_COUNT + 1][n];
  }

  void compute(
    List<Decoration> armorDecos,
    List<Decoration> weaponDecos,
    List<AmuletOpt> amuOptions,
    List<WeaponOpt> wepOptions,
    EnumMap<ArmorSlot, List<ArmorPiece>> bySlot
  ) {
    for (AmuletOpt a : amuOptions) {
      for (int i = 0; i < n; i++) {
        bestAmu[i] = Math.max(bestAmu[i], SkillLevels.levelAt(a.pack(), i));
      }
    }

    for (WeaponOpt w : wepOptions) {
      weaponMaxSlotValue = Math.max(weaponMaxSlotValue, w.slotValue());

      for (int i = 0; i < n; i++) {
        bestWeaponInnate[i] = Math.max(bestWeaponInnate[i], SkillLevels.levelAt(w.innatePack(), i));
      }
    }

    for (Decoration d : armorDecos) {
      long pack = SkillLevels.packOf(d.skills(), skillPos);

      for (int i = 0; i < n; i++) {
        bestArmorDeco[i] = Math.max(bestArmorDeco[i], SkillLevels.levelAt(pack, i));
      }
    }

    for (Decoration d : weaponDecos) {
      long pack = SkillLevels.packOf(d.skills(), skillPos);

      for (int i = 0; i < n; i++) {
        bestWeaponDeco[i] = Math.max(bestWeaponDeco[i], SkillLevels.levelAt(pack, i));
      }
    }

    for (Decoration d : armorDecos) {
      addDeco(d, armorDecoBest, armorDecoBestTotal);
    }

    for (Decoration d : weaponDecos) {
      addDeco(d, weaponDecoBest, weaponDecoBestTotal);
    }

    int[][] posMaxSkill = new int[ARMOR_SLOT_COUNT][n];
    int[] posMaxSlots = new int[ARMOR_SLOT_COUNT];

    for (int pos = 0; pos < ARMOR_SLOT_COUNT; pos++) {
      ArmorSlot slot = ArmorSlot.values()[pos];

      for (ArmorPiece p : bySlot.get(slot)) {
        posMaxSlots[pos] = Math.max(posMaxSlots[pos], p.slotCount());

        for (int i = 0; i < n; i++) {
          int level = p.skills().getOrDefault(nonSetIds.get(i), 0);
          posMaxSkill[pos][i] = Math.max(posMaxSkill[pos][i], level);
        }
      }
    }

    for (int pos = ARMOR_SLOT_COUNT - 1; pos >= 0; pos--) {
      suffixSlots[pos] = suffixSlots[pos + 1] + posMaxSlots[pos];

      for (int i = 0; i < n; i++) {
        suffixSkill[pos][i] = suffixSkill[pos + 1][i] + posMaxSkill[pos][i];
      }
    }
  }

  long residualCeiling(int skill, int[] armorCount) {
    return (
      bestAmu[skill] +
      bestWeaponInnate[skill] +
      slotCover(armorCount, armorDecoBest, skill) +
      SLOT_SIZES * bestWeaponDeco[skill]
    );
  }

  boolean canStillMeet(int skill, int innate, int totalSlots) {
    return (
      innate +
        bestAmu[skill] +
        bestWeaponInnate[skill] +
        (long) totalSlots * bestArmorDeco[skill] +
        SLOT_SIZES * bestWeaponDeco[skill] >=
      required[skill]
    );
  }

  boolean canStillMeet(int pos, int skill, int innate, int totalSlots) {
    return (
      innate +
        suffixSkill[pos][skill] +
        bestAmu[skill] +
        bestWeaponInnate[skill] +
        (long) (totalSlots + suffixSlots[pos]) * bestArmorDeco[skill] +
        SLOT_SIZES * bestWeaponDeco[skill] >=
      required[skill]
    );
  }

  static long slotCover(int[] counts, int[][] best, int skill) {
    long cover = 0;

    for (int s = 0; s < SLOT_SIZES; s++) {
      cover += (long) counts[s] * best[s][skill];
    }

    return cover;
  }

  boolean fillFeasible(long needW, int[] armorCount, int[] wepCount) {
    long needPoints = 0;

    for (int i = 0; i < n; i++) {
      int r = SkillLevels.levelAt(needW, i);

      if (r == 0) {
        continue;
      }

      needPoints += r;

      if (r > slotCover(armorCount, armorDecoBest, i) + slotCover(wepCount, weaponDecoBest, i)) {
        return false;
      }
    }

    long slotPoints = 0;

    for (int s = 0; s < SLOT_SIZES; s++) {
      slotPoints +=
        (long) armorCount[s] * armorDecoBestTotal[s] + (long) wepCount[s] * weaponDecoBestTotal[s];
    }

    return needPoints <= slotPoints;
  }

  long upperBound(long slotValue, int worn, long bonus) {
    long upper = slotValue;

    if (bonus > 0) {
      upper += bonus * (ARMOR_SLOT_COUNT - worn);
      upper += bonus;
      upper += Math.max(weaponMaxSlotValue, bonus);
    } else {
      upper += weaponMaxSlotValue;
    }

    return upper;
  }

  private void addDeco(Decoration d, int[][] best, int[] bestTotal) {
    long pack = SkillLevels.packOf(d.skills(), skillPos);

    if (pack == 0) {
      return;
    }

    int total = 0;

    for (int i = 0; i < n; i++) {
      total += SkillLevels.levelAt(pack, i);
    }

    for (int s = d.level() - 1; s < SLOT_SIZES; s++) {
      for (int i = 0; i < n; i++) {
        best[s][i] = Math.max(best[s][i], SkillLevels.levelAt(pack, i));
      }

      bestTotal[s] = Math.max(bestTotal[s], total);
    }
  }
}
