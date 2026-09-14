package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;

/**
 * Collapses armor combinations into aggregate signatures — capped required-skill levels, slot
 * multiset, set-bonus piece counts, worn-slot subset — keeping only the highest-defense
 * representative of each.
 */
final class ArmorAggregates {

  static final int SLOT_COUNT = ArmorSlot.values().length;
  private static final int MAX_SLOTS_OF_SIZE = 15;
  private static final int MAX_SET_PIECES = 7;
  private static final int WORN_SHIFT = 12;
  private static final int SET_SHIFT = WORN_SHIFT + 3;

  record Signature(long requiredLevels, long extras) {}

  record Aggregate(
    Signature signature,
    int[] slotCounts,
    int worn,
    int defense,
    long slotValue,
    ArmorPiece[] pieces
  ) {}

  private static final class Builder {

    long keyA;
    final int[] slotCounts = new int[3];
    final int[] setCount;
    int worn;
    int defense;
    long slotValue;
    final ArmorPiece[] pieces = new ArmorPiece[SLOT_COUNT];

    Builder(int nSet) {
      this.setCount = new int[nSet];
    }
  }

  private final int n;
  private final int nSet;
  private final List<Integer> setIds;
  private final Map<Integer, Integer> skillPos;
  private final int[] maxRank;
  private final List<Map<Integer, Integer>> setRanks;
  private final int[] requiredSet;
  private final SearchBounds bounds;

  ArmorAggregates(
    int n,
    List<Integer> setIds,
    Map<Integer, Integer> skillPos,
    int[] maxRank,
    List<Map<Integer, Integer>> setRanks,
    int[] requiredSet,
    SearchBounds bounds
  ) {
    this.n = n;
    this.nSet = setIds.size();
    this.setIds = setIds;
    this.skillPos = skillPos;
    this.maxRank = maxRank;
    this.setRanks = setRanks;
    this.requiredSet = requiredSet;
    this.bounds = bounds;
  }

  List<Aggregate> enumerate(EnumMap<ArmorSlot, List<ArmorPiece>> bySlot, long bonus) {
    Map<Signature, Builder> dp = new HashMap<>();
    dp.put(new Signature(0, 0), new Builder(nSet));

    for (int pos = 0; pos < SLOT_COUNT; pos++) {
      List<ArmorPiece> candidates = new ArrayList<>(bySlot.get(ArmorSlot.values()[pos]));

      if (bonus > 0) {
        candidates.add(null);
      }

      Map<Signature, Builder> next = new HashMap<>(dp.size() * 2);

      for (Map.Entry<Signature, Builder> e : dp.entrySet()) {
        Builder v = e.getValue();

        for (ArmorPiece p : candidates) {
          Builder nv = extend(v, pos, p);

          if (nv == null) {
            continue;
          }

          Signature key = keyOf(nv);
          Builder old = next.get(key);

          if (old == null || nv.defense > old.defense) {
            next.put(key, nv);
          }
        }
      }

      dp = next;

      if (dp.isEmpty()) {
        return List.of();
      }
    }

    List<Aggregate> aggregates = new ArrayList<>(dp.size());

    for (Map.Entry<Signature, Builder> e : dp.entrySet()) {
      Builder v = e.getValue();
      boolean ok = true;

      for (int j = 0; j < nSet; j++) {
        if (activationLevel(setRanks.get(j), v.setCount[j]) < requiredSet[j]) {
          ok = false;
          break;
        }
      }

      if (!ok) {
        continue;
      }

      aggregates.add(
        new Aggregate(
          e.getKey(),
          v.slotCounts.clone(),
          v.worn,
          v.defense,
          v.slotValue,
          v.pieces.clone()
        )
      );
    }

    return aggregates;
  }

  /** Highest skill level achieved by {@code pieceCount} pieces under the bonus thresholds. */
  static int activationLevel(Map<Integer, Integer> thresholds, int pieceCount) {
    int best = 0;

    for (Map.Entry<Integer, Integer> rank : thresholds.entrySet()) {
      if (pieceCount >= rank.getKey() && rank.getValue() > best) {
        best = rank.getValue();
      }
    }

    return best;
  }

  private Builder extend(Builder v, int pos, ArmorPiece p) {
    Builder r = new Builder(nSet);
    r.keyA = v.keyA;
    r.worn = v.worn;
    r.defense = v.defense;
    r.slotValue = v.slotValue;
    System.arraycopy(v.slotCounts, 0, r.slotCounts, 0, 3);
    System.arraycopy(v.setCount, 0, r.setCount, 0, v.setCount.length);
    System.arraycopy(v.pieces, 0, r.pieces, 0, SLOT_COUNT);

    if (p == null) {
      r.pieces[pos] = null;
    } else {
      r.pieces[pos] = p;
      r.worn++;
      r.defense += p.maxDefense();

      for (Map.Entry<Integer, Integer> se : p.skills().entrySet()) {
        Integer skillPosIdx = skillPos.get(se.getKey());

        if (skillPosIdx == null) {
          continue;
        }

        int cur = SkillLevels.levelAt(r.keyA, skillPosIdx);
        int next = Math.min(cur + se.getValue(), maxRank[skillPosIdx]);
        r.keyA = SkillLevels.setLevel(r.keyA, skillPosIdx, next);
      }

      for (int s : p.slots()) {
        if (s >= 1 && s <= 3) {
          r.slotCounts[s - 1] = Math.min(MAX_SLOTS_OF_SIZE, r.slotCounts[s - 1] + 1);
        }

        r.slotValue += SlotScores.valueOf(s);
      }

      for (int j = 0; j < nSet; j++) {
        int sid = setIds.get(j);

        if (p.setBonusId() == sid || p.groupBonusId() == sid) {
          r.setCount[j] = Math.min(MAX_SET_PIECES, r.setCount[j] + 1);
        }
      }
    }

    if (!feasiblePartial(r, pos + 1)) {
      return null;
    }

    return r;
  }

  private boolean feasiblePartial(Builder r, int chosen) {
    if (chosen >= SLOT_COUNT) {
      for (int i = 0; i < n; i++) {
        int innate = SkillLevels.levelAt(r.keyA, i);

        if (!bounds.canStillMeet(i, innate, totalSlots(r.slotCounts))) {
          return false;
        }
      }

      for (int j = 0; j < nSet; j++) {
        if (activationLevel(setRanks.get(j), r.setCount[j]) < requiredSet[j]) {
          return false;
        }
      }

      return true;
    }

    int piecesLeft = SLOT_COUNT - chosen;

    for (int i = 0; i < n; i++) {
      int innate = SkillLevels.levelAt(r.keyA, i);

      if (!bounds.canStillMeet(chosen, i, innate, totalSlots(r.slotCounts))) {
        return false;
      }
    }

    for (int j = 0; j < nSet; j++) {
      if (activationLevel(setRanks.get(j), r.setCount[j] + piecesLeft) < requiredSet[j]) {
        return false;
      }
    }

    return true;
  }

  private static int totalSlots(int[] slotCounts) {
    return slotCounts[0] + slotCounts[1] + slotCounts[2];
  }

  private Signature keyOf(Builder v) {
    long extras = 0;

    for (int sz = 1; sz <= 3; sz++) {
      extras |= (long) v.slotCounts[sz - 1] << (4 * (sz - 1));
    }

    extras |= (long) v.worn << WORN_SHIFT;

    for (int j = 0; j < v.setCount.length; j++) {
      extras |= (long) v.setCount[j] << (SET_SHIFT + 3 * j);
    }

    return new Signature(v.keyA, extras);
  }
}
