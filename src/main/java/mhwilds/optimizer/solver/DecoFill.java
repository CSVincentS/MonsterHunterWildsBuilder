package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.Decoration;
import mhwilds.optimizer.model.SlotScore;

public final class DecoFill {

  static final long INF = Long.MAX_VALUE >> 2;
  private static final int SIZES = SlotScore.MAX_SLOT_SIZE;

  static final int GROUPS = 2 * SIZES;

  public static final int ARMOR = 0;
  public static final int WEAPON = 1;

  private final int n;
  private final long needMask;
  private final List<DecoEntry>[] groups;
  private final int[] groupCost;
  private final Map<Long, Long> cache = new HashMap<>();

  private static final class DecoEntry {

    final long pack;
    final Decoration deco;

    DecoEntry(long pack, Decoration deco) {
      this.pack = pack;
      this.deco = deco;
    }
  }

  @SuppressWarnings("unchecked")
  public DecoFill(
    List<Decoration> armorDecos,
    List<Decoration> weaponDecos,
    Map<Integer, Integer> skillPositions
  ) {
    this.n = skillPositions.size();
    SkillLevels.requireCapacity(n, GROUPS, "DecoFill");
    this.needMask = SkillLevels.mask(n);
    this.groups = new List[GROUPS];
    this.groupCost = new int[GROUPS];

    for (int g = 0; g < GROUPS; g++) {
      groups[g] = new ArrayList<>();
    }

    for (int size = 1; size <= SIZES; size++) {
      groupCost[group(ARMOR, size)] = (int) SlotScore.valueOf(size);
      groupCost[group(WEAPON, size)] = (int) SlotScore.valueOf(size);
    }

    add(armorDecos, ARMOR, skillPositions);
    add(weaponDecos, WEAPON, skillPositions);

    for (int g = 0; g < GROUPS; g++) {
      groups[g] = prune(groups[g]);
    }
  }

  public record FillResult(long cost, List<Step> steps) {}

  public record Step(int kind, int size, Decoration deco) {}

  public long fillCost(long needPack, int[] armorCount, int[] weaponCount) {
    if (SkillLevels.isZero(needPack, n)) {
      return 0;
    }

    long counts = packCounts(armorCount, weaponCount);
    long key = (counts << (SkillLevels.BITS_PER_SKILL * n)) | needPack;
    Long cached = cache.get(key);

    if (cached != null) {
      return cached;
    }

    long cost = fillImpl(needPack, counts, false).cost();

    cache.put(key, cost);
    return cost;
  }

  public FillResult fillWithSteps(long needPack, int[] armorCount, int[] weaponCount) {
    if (SkillLevels.isZero(needPack, n)) {
      return new FillResult(0, List.of());
    }

    return fillImpl(needPack, packCounts(armorCount, weaponCount), true);
  }

  private static int group(int kind, int size) {
    return kind * SIZES + (size - 1);
  }

  private long packCounts(int[] armorCount, int[] weaponCount) {
    long counts = 0;

    for (int size = 1; size <= SIZES; size++) {
      counts |=
        (long) (armorCount[size - 1] & 0xF) << (SkillLevels.NIBBLE_BITS * group(ARMOR, size));
      counts |=
        (long) (weaponCount[size - 1] & 0xF) << (SkillLevels.NIBBLE_BITS * group(WEAPON, size));
    }

    return counts;
  }

  private void add(List<Decoration> decos, int kind, Map<Integer, Integer> skillPositions) {
    if (decos == null) {
      return;
    }

    for (Decoration deco : decos) {
      long pack = SkillLevels.packOf(deco.skills(), skillPositions);

      if (pack == 0) {
        continue;
      }

      for (int size = deco.level(); size <= SIZES; size++) {
        groups[group(kind, size)].add(new DecoEntry(pack, deco));
      }
    }
  }

  private static List<DecoEntry> prune(List<DecoEntry> in) {
    if (in.size() <= 1) {
      return in;
    }

    List<DecoEntry> out = new ArrayList<>(in.size());

    outer: for (DecoEntry a : in) {
      for (DecoEntry b : in) {
        if (b == a) {
          continue;
        }

        if (dominates(b.pack, a.pack)) {
          continue outer;
        }
      }

      out.add(a);
    }

    return out;
  }

  private static boolean dominates(long b, long a) {
    boolean strict = false;

    for (int pos = 0; pos < SkillLevels.MAX_PACKED_SKILLS; pos++) {
      int bv = (int) ((b >>> (SkillLevels.BITS_PER_SKILL * pos)) & 0xF);
      int av = (int) ((a >>> (SkillLevels.BITS_PER_SKILL * pos)) & 0xF);

      if (bv < av) {
        return false;
      }

      if (bv > av) {
        strict = true;
      }
    }

    return strict;
  }

  private FillResult fillImpl(long needPack, long counts, boolean wantSteps) {
    java.util.PriorityQueue<long[]> queue = new java.util.PriorityQueue<>((x, y) ->
      Long.compare(x[0], y[0])
    );
    Map<Long, Long> dist = new HashMap<>();
    Map<Long, Long> prevState = wantSteps ? new HashMap<>() : null;
    Map<Long, int[]> prevStep = wantSteps ? new HashMap<>() : null;

    long start = (counts << (SkillLevels.BITS_PER_SKILL * n)) | needPack;
    dist.put(start, 0L);
    queue.add(new long[] { 0, start });

    long goalState = -1;
    long goalCost = INF;

    while (!queue.isEmpty()) {
      long[] cur = queue.poll();
      long cost = cur[0];
      long state = cur[1];

      if (cost > dist.getOrDefault(state, INF)) {
        continue;
      }

      long need = state & needMask;

      if (need == 0) {
        goalState = state;
        goalCost = cost;
        break;
      }

      long countsLeft = state >>> (SkillLevels.BITS_PER_SKILL * n);

      for (int g = 0; g < GROUPS; g++) {
        int count = (int) ((countsLeft >>> (SkillLevels.NIBBLE_BITS * g)) & 0xF);

        if (count == 0 || groups[g].isEmpty()) {
          continue;
        }

        long newCounts = countsLeft - (1L << (SkillLevels.NIBBLE_BITS * g));

        for (int di = 0; di < groups[g].size(); di++) {
          long newNeed = SkillLevels.sub(need, groups[g].get(di).pack);

          if (newNeed == need) {
            continue;
          }

          long next = (newCounts << (SkillLevels.BITS_PER_SKILL * n)) | newNeed;
          long nextCost = cost + groupCost[g];

          if (nextCost < dist.getOrDefault(next, INF)) {
            dist.put(next, nextCost);
            queue.add(new long[] { nextCost, next });

            if (wantSteps) {
              prevState.put(next, state);
              prevStep.put(next, new int[] { g, di });
            }
          }
        }
      }
    }

    if (goalCost == INF) {
      return new FillResult(INF, List.of());
    }

    List<Step> steps = new ArrayList<>();

    if (wantSteps) {
      long s = goalState;

      while (prevState.containsKey(s)) {
        int[] op = prevStep.get(s);
        int g = op[0];
        int kind = g / SIZES;
        int size = (g % SIZES) + 1;
        steps.add(new Step(kind, size, groups[g].get(op[1]).deco));
        s = prevState.get(s);
      }

      java.util.Collections.reverse(steps);
    }

    return new FillResult(goalCost, steps);
  }
}
