package mhwilds.optimizer.ranking;

import mhwilds.optimizer.model.Build;

abstract class ScoreRanking implements RankingStrategy {

  abstract long scoreOf(Build build);

  @Override
  public int compare(Build a, Build b) {
    int byScore = Long.compare(scoreOf(b), scoreOf(a));

    if (byScore != 0) {
      return byScore;
    }

    return Integer.compare(b.totalDefense(), a.totalDefense());
  }
}
