package mhwilds.optimizer.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mhwilds.optimizer.config.GearPoolConfig;
import mhwilds.optimizer.config.GearPoolResolver;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.loader.GameDataLoader;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.Build;
import org.junit.jupiter.api.Test;

/** Prototype check: feeding the solver's pool through ParetoFilter must not regress results. */
class ParetoFilterTest {

  @Test
  void filteredPoolDoesNotRegressAnyRawScore() {
    GameData data = new GameDataLoader("data").load();

    for (String path : List.of(
      "configs/gogma_meta.json",
      "configs/sere-gore_adrenaline.json",
      "configs/zoh-gore_evasion.json"
    )) {
      GearPoolConfig config = GearPoolConfig.load(path);
      SolverPool raw = new GearPoolResolver(data).resolve(config);

      int before = raw.armorPieces().size();
      List<ArmorPiece> filtered = ParetoFilter.filter(raw.armorPieces(), raw.thresholds());
      SolverPool pared = new SolverPool(
        filtered,
        raw.armorDecorations(),
        raw.weaponDecorations(),
        raw.amuletRanks(),
        raw.weapons(),
        raw.skillMap(),
        raw.thresholds()
      );

      long bonus = config.equipmentSlotBonus() != null ? config.equipmentSlotBonus() : 0L;
      GreedySolver rawSolver = new GreedySolver(1000, 1, bonus);
      GreedySolver paredSolver = new GreedySolver(1000, 1, bonus);

      long t0 = System.currentTimeMillis();
      List<Build> rawResults = rawSolver.solve(raw);
      long tRaw = System.currentTimeMillis() - t0;

      long t1 = System.currentTimeMillis();
      List<Build> paredResults = paredSolver.solve(pared);
      long tPared = System.currentTimeMillis() - t1;

      List<Long> rawScores = rawResults
        .stream()
        .map(b -> scoreOf(b, bonus))
        .sorted()
        .toList();
      List<Long> paredScores = paredResults
        .stream()
        .map(b -> scoreOf(b, bonus))
        .sorted()
        .toList();

      Set<Integer> rawIds = new HashSet<>();
      Set<Integer> paredIds = new HashSet<>();

      for (ArmorPiece up : raw.armorPieces()) {
        rawIds.add(up.gameId());
      }
      for (ArmorPiece up : pared.armorPieces()) {
        paredIds.add(up.gameId());
      }

      long rawUsingRemoved = rawResults
        .stream()
        .filter(b -> {
          ArmorPiece[] pieces = b.armorPieces();

          for (ArmorPiece p : pieces) {
            if (p != null && !paredIds.contains(p.gameId())) {
              return true;
            }
          }

          return false;
        })
        .count();

      // Rank-aligned invariant: every score the raw solver can achieve, the pared pool can
      // achieve at least as well, at every rank.
      List<Long> rawDesc = rawResults
        .stream()
        .map(b -> scoreOf(b, bonus))
        .sorted(Comparator.reverseOrder())
        .toList();
      List<Long> paredDesc = paredResults
        .stream()
        .map(b -> scoreOf(b, bonus))
        .sorted(Comparator.reverseOrder())
        .toList();

      int worse = 0;

      for (int i = 0; i < Math.min(rawDesc.size(), paredDesc.size()); i++) {
        if (paredDesc.get(i) < rawDesc.get(i)) {
          worse++;
        }
      }

      System.out.printf(
        "[%s] armor %d -> %d | raw %d (%dms) pared %d (%dms) | builds using removed pieces: %d | ranks pared<raw: %d%n",
        path,
        before,
        filtered.size(),
        rawResults.size(),
        tRaw,
        paredResults.size(),
        tPared,
        rawUsingRemoved,
        worse
      );

      assertThat(paredDesc.size())
        .as("%s: pared pool achieves every raw score (count)", path)
        .isGreaterThanOrEqualTo(rawDesc.size());

      for (int i = 0; i < rawDesc.size(); i++) {
        assertThat(paredDesc.get(i))
          .as("%s: score at rank %d must not regress", path, i)
          .isGreaterThanOrEqualTo(rawDesc.get(i));
      }
    }
  }

  private static long scoreOf(Build b, long bonus) {
    return b.equipmentAwareScore(bonus);
  }
}
