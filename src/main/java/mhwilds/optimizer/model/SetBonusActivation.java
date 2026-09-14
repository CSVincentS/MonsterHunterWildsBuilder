package mhwilds.optimizer.model;

import java.util.Map;

public final class SetBonusActivation {

  private SetBonusActivation() {}

  public static int levelFor(int pieceCount, Map<Integer, Integer> thresholds) {
    if (thresholds == null) {
      return 0;
    }

    int best = 0;

    for (Map.Entry<Integer, Integer> rank : thresholds.entrySet()) {
      if (pieceCount >= rank.getKey() && rank.getValue() > best) {
        best = rank.getValue();
      }
    }

    return best;
  }
}
