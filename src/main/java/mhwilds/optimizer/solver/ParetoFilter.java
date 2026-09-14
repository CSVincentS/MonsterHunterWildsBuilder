package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mhwilds.optimizer.model.ArmorPiece;

/**
 * Sound exact reduction of the armor candidate space: within each armor slot, keeps only
 * non-dominated pieces.
 *
 * <p>A piece V dominates a piece U in the same slot when V is at least as good on every dimension
 * that a build can depend on, so U can never appear in an optimal build:
 *
 * <ul>
 *   <li>same deco-slot size pattern (identical multiset of slot sizes),
 *   <li>same match on the required set/group bonuses,
 *   <li>at least U's levels on every required skill,
 *   <li>at least U's max defense,
 * </ul>
 *
 * with at least one strict beat somewhere.
 *
 * <p>Equality of the slot-size pattern is essential, not a convenience: under the free-slot-score
 * objective a consumed slot forfeits its {@code 10^size} value, so a piece with larger slots is not
 * strictly better — e.g. a {@code [3,1]} piece is cheaper to fill one slot with than a {@code [2,1]}
 * piece is to fill both. Only pieces with identical slot patterns are mutually comparable, and then
 * only their skills and defense can differ.
 */
public final class ParetoFilter {

  private ParetoFilter() {}

  /** Returns a filtered copy of {@code pieces} containing only per-slot non-dominated pieces. */
  public static List<ArmorPiece> filter(List<ArmorPiece> pieces, SkillThresholds thresholds) {
    Set<Integer> required = thresholds.requiredSkills().keySet();
    Map<String, List<ArmorPiece>> groups = new HashMap<>();

    for (ArmorPiece piece : pieces) {
      groups.computeIfAbsent(groupKey(piece, required), k -> new ArrayList<>()).add(piece);
    }

    List<ArmorPiece> out = new ArrayList<>();

    for (List<ArmorPiece> group : groups.values()) {
      out.addAll(nonDominated(group, required));
    }

    out.sort(Comparator.comparing(ArmorPiece::kind).thenComparingInt(ArmorPiece::gameId));

    return out;
  }

  private static String groupKey(ArmorPiece piece, Set<Integer> required) {
    StringBuilder sb = new StringBuilder();
    sb.append(piece.kind().name()).append('|');

    // A dominated piece must offer the exact same deco capacity, so only pieces with an
    // identical slot-size pattern are comparable.
    int[] slots = piece.slots().clone();
    Arrays.sort(slots);

    for (int s : slots) {
      sb.append(s).append(',');
    }

    sb.append('|');

    // Pieces contribute to set-skill requirements through their bonus IDs; only pieces matching
    // the exact same set of required set skills are mutually comparable.
    List<Integer> matched = new ArrayList<>();

    if (required.contains(piece.setBonusId())) {
      matched.add(piece.setBonusId());
    }

    if (required.contains(piece.groupBonusId())) {
      matched.add(piece.groupBonusId());
    }

    matched.sort(Comparator.naturalOrder());

    for (int bonus : matched) {
      sb.append(bonus).append('.');
    }

    return sb.toString();
  }

  private static List<ArmorPiece> nonDominated(List<ArmorPiece> group, Set<Integer> required) {
    List<Integer> reqList = required.stream().sorted().toList();

    List<ArmorPiece> keep = new ArrayList<>(group.size());

    outer: for (ArmorPiece u : group) {
      for (ArmorPiece v : group) {
        if (v == u) {
          continue;
        }

        if (dominates(v, u, reqList)) {
          // u is beaten by a peer in the same equivalence class; never needed.
          continue outer;
        }
      }

      keep.add(u);
    }

    return keep;
  }

  private static boolean dominates(ArmorPiece v, ArmorPiece u, List<Integer> requiredSkillIds) {
    int skillGain = 0;

    for (int skillId : requiredSkillIds) {
      int vs = v.skills().getOrDefault(skillId, 0);
      int us = u.skills().getOrDefault(skillId, 0);

      if (vs < us) {
        return false;
      }

      skillGain |= vs > us ? 1 : 0;
    }

    if (v.maxDefense() < u.maxDefense()) {
      return false;
    }

    int defenseGain = v.maxDefense() > u.maxDefense() ? 1 : 0;

    return skillGain != 0 || defenseGain != 0;
  }
}
