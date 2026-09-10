package mhwilds.optimizer.solver;

import java.util.List;
import mhwilds.optimizer.model.Build;

public interface Solver {
  /** Returns the solved builds for a pool, ordered best-first. */
  List<Build> solve(SolverPool pool);
}
