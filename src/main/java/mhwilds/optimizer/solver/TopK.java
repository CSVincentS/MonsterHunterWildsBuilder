package mhwilds.optimizer.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import mhwilds.optimizer.model.Build;

/**
 * Score-only heap of up to {@code k} builds that only accepts strictly-better scores at the
 * boundary, ordered best-first by score then total defense.
 */
final class TopK {

  private final int k;
  private final long bonus;
  private final PriorityQueue<Build> heap;
  private long worst;

  TopK(int k, long bonus) {
    this.k = k;
    this.bonus = bonus;
    this.heap = new PriorityQueue<>(Comparator.comparingLong(this::scoreOf));
  }

  boolean isFull() {
    return heap.size() >= k;
  }

  long worst() {
    return worst;
  }

  /** True when {@code score} is good enough to displace the current {@link #worst}. */
  boolean accepts(long score) {
    return heap.size() < k || score > worst;
  }

  void offer(Build build, long score) {
    if (heap.size() < k) {
      heap.add(build);
    } else if (score > worst) {
      heap.poll();
      heap.add(build);
    }

    worst = scoreOf(heap.peek());
  }

  List<Build> sorted() {
    List<Build> out = new ArrayList<>(heap);
    out.sort(
      Comparator.comparingLong(this::scoreOf)
        .reversed()
        .thenComparing(Comparator.comparingInt(Build::totalDefense).reversed())
    );

    return out;
  }

  private long scoreOf(Build b) {
    return b.equipmentAwareScore(bonus);
  }
}
