package mhwilds.optimizer.solver;

import java.util.Map;

final class SkillLevels {

  static final int NIBBLE_BITS = 4;

  static final int BITS_PER_SKILL = NIBBLE_BITS;
  private static final long SKILL_MASK = 0xF;

  /** 15 nibbles (60 bits): never touches the sign bit. */
  private static final int PACK_BUDGET_BITS = 60;

  /** Probed positions; 16 keeps shift logic unambiguous. */
  static final int MAX_PACKED_SKILLS = 16;

  private SkillLevels() {}

  static int maxSkills(int extraGroups) {
    return (PACK_BUDGET_BITS - BITS_PER_SKILL * extraGroups) / BITS_PER_SKILL;
  }

  static void requireCapacity(int n, int extraGroups, String what) {
    if (BITS_PER_SKILL * n + BITS_PER_SKILL * extraGroups > PACK_BUDGET_BITS) {
      throw new IllegalArgumentException(
        what + " supports at most " + maxSkills(extraGroups) + " required non-set skills, got " + n
      );
    }
  }

  static long mask(int n) {
    return (1L << (BITS_PER_SKILL * n)) - 1;
  }

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

  /** Pointwise clamped subtraction. */
  static long sub(long need, long by) {
    long out = 0;

    for (int pos = 0; pos < MAX_PACKED_SKILLS; pos++) {
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
