package mhwilds.optimizer.solver;

import java.util.Map;

/** Four-bit-per-skill bit packing over the required non-set skills. */
final class SkillLevels {

  static final int BITS_PER_SKILL = 4;
  private static final long SKILL_MASK = 0xF;

  private SkillLevels() {}

  /** Highest number of non-set skills that fit alongside {@code extraGroups} packed groups. */
  static int maxSkills(int extraGroups) {
    return (60 - BITS_PER_SKILL * extraGroups) / BITS_PER_SKILL;
  }

  static void requireCapacity(int n, int extraGroups, String what) {
    if (BITS_PER_SKILL * n + BITS_PER_SKILL * extraGroups > 60) {
      throw new IllegalArgumentException(
        what + " supports at most " + maxSkills(extraGroups) + " required non-set skills, got " + n
      );
    }
  }

  /** Bitmask covering the {@code n} packed skills. */
  static long mask(int n) {
    return (1L << (BITS_PER_SKILL * n)) - 1;
  }

  /** Packs {@code skills} into their {@code positions}; skills without a position are skipped. */
  static long packOf(Map<Integer, Integer> skills, Map<Integer, Integer> positions) {
    long pack = 0;

    for (Map.Entry<Integer, Integer> e : skills.entrySet()) {
      Integer pos = positions.get(e.getKey());

      if (pos != null) {
        pack |= (long) (e.getValue() & SKILL_MASK) << (BITS_PER_SKILL * pos);
      }
    }

    return pack;
  }

  /** Packs {@code levels[i]} (each {@code 0..15}) into four bits per skill. */
  static long packLevels(int[] levels, int n) {
    long pack = 0;

    for (int i = 0; i < n; i++) {
      pack |= (long) (levels[i] & SKILL_MASK) << (BITS_PER_SKILL * i);
    }

    return pack;
  }

  static int levelAt(long pack, int pos) {
    return (int) ((pack >>> (BITS_PER_SKILL * pos)) & SKILL_MASK);
  }

  static long setLevel(long pack, int pos, int level) {
    return (
      (pack & ~(SKILL_MASK << (BITS_PER_SKILL * pos))) |
      ((long) (level & SKILL_MASK) << (BITS_PER_SKILL * pos))
    );
  }

  /** Subtracts {@code by} from {@code need} pointwise, clamping at zero. */
  static long sub(long need, long by) {
    long out = 0;

    for (int pos = 0; pos < 16; pos++) {
      int value =
        (int) ((need >>> (BITS_PER_SKILL * pos)) & SKILL_MASK) -
        (int) ((by >>> (BITS_PER_SKILL * pos)) & SKILL_MASK);

      if (value > 0) {
        out |= (long) value << (BITS_PER_SKILL * pos);
      }
    }

    return out;
  }

  static boolean isZero(long need, int n) {
    return (need & mask(n)) == 0;
  }
}
