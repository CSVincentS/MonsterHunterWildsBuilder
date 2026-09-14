package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.Decoration;

/**
 * Exact minimum-cost decoration fill for a fixed slot multiset.
 *
 * <p>Given the residual skill levels a build still needs and a multiset of available decoration
 * slots (armor first, weapon second), finds the assignment that uses the smallest total free-slot
 * value, or {@link #INF} when no assignment covers the residual. Slots of the same size are
 * interchangeable, so the problem is a shortest-path over (residual packed, remaining counts
 * packed) states: spending one slot of a group costs {@code 10^size} and reduces the residual by a
 * non-dominated decoration's skills.
 *
 * <p>Skill levels are packed four bits per required non-set skill, and slot counts four bits per
 * (kind, size) group — up to 9 required non-set skills fit in the state long. Repeats are cached
 * so the identical fill is computed once per (slot multiset, residual) pair.
 */
public final class DecoFill {

  private static final long[] POW10 = { 1, 10, 100, 1000, 10000, 100000, 1000000 };
  static final long INF = Long.MAX_VALUE >> 2;
  private static final int BITS = 4;
  private static final int SIZES = 3;
  private static final int GROUPS = 6;

  /** Armor decorations and weapon decorations use {@code group = kind * SIZES + (size - 1)}. */
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

  /** Packs {@code levels[i]} (each {@code 0..15}) into four bits per skill. */
  public static long packLevels(int[] levels, int n) {
    long pack = 0;

    for (int i = 0; i < n; i++) {
      pack |= (long) (levels[i] & 0xF) << (4 * i);
    }

    return pack;
  }

  /** Subtracts {@code by} from {@code need} pointwise, clamping at zero. */
  public static long sub(long need, long by) {
    long out = 0;

    for (int pos = 0; pos < 16; pos++) {
      int value = (int) ((need >>> (4 * pos)) & 0xF) - (int) ((by >>> (4 * pos)) & 0xF);

      if (value > 0) {
        out |= (long) value << (4 * pos);
      }
    }

    return out;
  }

  public static boolean isZero(long need, int n) {
    return (need & ((1L << (4 * n)) - 1)) == 0;
  }

  /**
   * @param armorDecos armor decoration pool
   * @param weaponDecos weapon decoration pool
   * @param skillPositions maps required non-set skill id to its four-bit packing position
   */
  @SuppressWarnings("unchecked")
  public DecoFill(
    List<Decoration> armorDecos,
    List<Decoration> weaponDecos,
    Map<Integer, Integer> skillPositions
  ) {
    this.n = skillPositions.size();

    if (4 * n + 4 * GROUPS > 60) {
      throw new IllegalArgumentException(
        "DecoFill supports at most 9 required non-set skills, got " + n
      );
    }

    this.needMask = (1L << (4 * n)) - 1;
    this.groups = new List[GROUPS];
    this.groupCost = new int[GROUPS];

    for (int g = 0; g < GROUPS; g++) {
      groups[g] = new ArrayList<>();
    }

    for (int size = 1; size <= SIZES; size++) {
      groupCost[group(ARMOR, size)] = (int) POW10[size];
      groupCost[group(WEAPON, size)] = (int) POW10[size];
    }

    add(armorDecos, ARMOR, skillPositions);
    add(weaponDecos, WEAPON, skillPositions);

    for (int g = 0; g < GROUPS; g++) {
      groups[g] = prune(groups[g]);
    }
  }

  /** A fill outcome; {@code steps} is only populated by {@link #fillWithSteps}. */
  public record FillResult(long cost, List<Step> steps) {}

  public record Step(int kind, int size, Decoration deco) {}

  /** Minimum consumed slot value covering {@code needPack}, or {@link #INF} when impossible. */
  public long fillCost(long needPack, int[] armorCount, int[] weaponCount) {
    if (isZero(needPack, n)) {
      return 0;
    }

    long counts = packCounts(armorCount, weaponCount);
    long key = (counts << (4 * n)) | needPack;
    Long cached = cache.get(key);

    if (cached != null) {
      return cached;
    }

    long cost = fillImpl(needPack, counts, false).cost();

    cache.put(key, cost);
    return cost;
  }

  /** Minimum fill plus the concrete decoration-per-slot assignment (uncached). */
  public FillResult fillWithSteps(long needPack, int[] armorCount, int[] weaponCount) {
    if (isZero(needPack, n)) {
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
      counts |= (long) (armorCount[size - 1] & 0xF) << (4 * group(ARMOR, size));
      counts |= (long) (weaponCount[size - 1] & 0xF) << (4 * group(WEAPON, size));
    }

    return counts;
  }

  private void add(List<Decoration> decos, int kind, Map<Integer, Integer> skillPositions) {
    if (decos == null) {
      return;
    }

    for (Decoration deco : decos) {
      long pack = 0;

      for (Map.Entry<Integer, Integer> e : deco.skills().entrySet()) {
        Integer pos = skillPositions.get(e.getKey());

        if (pos != null) {
          pack |= (long) (e.getValue() & 0xF) << (4 * pos);
        }
      }

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

    for (int pos = 0; pos < 16; pos++) {
      int bv = (int) ((b >>> (4 * pos)) & 0xF);
      int av = (int) ((a >>> (4 * pos)) & 0xF);

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

    long start = (counts << (4 * n)) | needPack;
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

      long countsLeft = state >>> (4 * n);

      for (int g = 0; g < GROUPS; g++) {
        int count = (int) ((countsLeft >>> (4 * g)) & 0xF);

        if (count == 0 || groups[g].isEmpty()) {
          continue;
        }

        long newCounts = countsLeft - (1L << (4 * g));

        for (int d = 0; d < groups[g].size(); d++) {
          long newNeed = sub(need, groups[g].get(d).pack);

          if (newNeed == need) {
            continue;
          }

          long next = (newCounts << (4 * n)) | newNeed;
          long nextCost = cost + groupCost[g];

          if (nextCost < dist.getOrDefault(next, INF)) {
            dist.put(next, nextCost);
            queue.add(new long[] { nextCost, next });

            if (wantSteps) {
              prevState.put(next, state);
              prevStep.put(next, new int[] { g, d });
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
