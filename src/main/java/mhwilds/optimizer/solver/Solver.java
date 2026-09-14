package mhwilds.optimizer.solver;

import java.util.List;
import mhwilds.optimizer.model.Build;

public interface Solver {
  List<Build> solve(SolverPool pool);
}
