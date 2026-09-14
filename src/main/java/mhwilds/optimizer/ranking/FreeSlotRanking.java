package mhwilds.optimizer.ranking;

import mhwilds.optimizer.model.Build;

public class FreeSlotRanking extends ScoreRanking {

  @Override
  long scoreOf(Build build) {
    return build.freeSlotScore();
  }
}
