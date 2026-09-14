package mhwilds.optimizer.solver;

/** Free-slot scoring: an empty slot of size {@code s} is worth {@code 10^s} points. */
final class SlotScores {

  static final long[] POW10 = { 1, 10, 100, 1000, 10000, 100000, 1000000 };

  private SlotScores() {}

  static long valueOf(int size) {
    return POW10[size];
  }

  static long score(int[] slots) {
    long s = 0;

    for (int sz : slots) {
      s += POW10[sz];
    }

    return s;
  }
}
