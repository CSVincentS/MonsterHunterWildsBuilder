package mhwilds.optimizer.ranking;

import mhwilds.optimizer.model.Build;

public class FreeSlotRanking implements RankingStrategy {

  @Override
  public int compare(Build a, Build b) {
    int byScore = Long.compare(b.freeSlotScore(), a.freeSlotScore());

    if (byScore != 0) {
      return byScore;
    }

    return Integer.compare(b.totalDefense(), a.totalDefense());
  }
}
