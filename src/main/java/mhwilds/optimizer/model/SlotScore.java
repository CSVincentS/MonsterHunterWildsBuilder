package mhwilds.optimizer.model;

public final class SlotScore {

  public static final int MAX_SLOT_SIZE = 3;

  private static final long[] POW10 = { 1, 10, 100, 1000, 10000, 100000, 1000000 };

  private SlotScore() {}

  public static long valueOf(int size) {
    return POW10[size];
  }

  public static long score(int[] slots) {
    long total = 0;

    for (int size : slots) {
      total += POW10[size];
    }

    return total;
  }
}
