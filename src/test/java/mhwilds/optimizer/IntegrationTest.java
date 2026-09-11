package mhwilds.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import mhwilds.optimizer.config.GearPoolConfig;
import mhwilds.optimizer.config.GearPoolResolver;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.loader.GameDataLoader;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.ranking.RankingFactory;
import mhwilds.optimizer.ranking.RankingStrategy;
import mhwilds.optimizer.solver.GreedySolver;
import mhwilds.optimizer.solver.SolverPool;
import mhwilds.optimizer.validity.BuildValidator;
import org.junit.jupiter.api.Test;

class IntegrationTest {

  private static final int CONSTITUTION_ID = -1689391744;
  private static final int STAMINA_SURGE_ID = -315492576;

  @Test
  void endToEndPipelineProducesValidTopKBuilds() {
    long start = System.currentTimeMillis();

    // Load real data
    GameData data = new GameDataLoader("data").load();
    GearPoolConfig config = GearPoolConfig.load("src/main/resources/default-config.json");
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    // Solve
    GreedySolver solver = new GreedySolver();
    List<Build> results = solver.solve(pool);

    long elapsed = System.currentTimeMillis() - start;

    // 1. Completes in reasonable time
    assertThat(elapsed)
      .as("Solver should complete within 30 seconds (was %d ms)", elapsed)
      .isLessThan(30_000);

    // 2. Returns a non-empty list of expected size
    assertThat(results)
      .as("Solver should find at least 1000 builds, found %d", results.size())
      .hasSizeGreaterThanOrEqualTo(1000);

    // Rank all results so we can validate the full set
    RankingStrategy ranking = RankingFactory.create("free_slots");
    List<Build> ranked = results.stream().sorted(ranking).toList();
    assertThat(ranked).hasSizeGreaterThanOrEqualTo(1000);

    // 3. Every build passes BuildValidator
    BuildValidator validator = new BuildValidator(pool.skillMap());
    long invalidCount = ranked
      .stream()
      .map(validator::validate)
      .filter(errors -> !errors.isEmpty())
      .count();
    assertThat(invalidCount)
      .as("All builds must pass BuildValidator (found %d invalid)", invalidCount)
      .isZero();

    // 4. Every build satisfies the seed skill thresholds
    for (Build build : ranked) {
      assertThat(build.totalSkillLevel(CONSTITUTION_ID))
        .as("Constitution must be >= 5 in every build")
        .isGreaterThanOrEqualTo(5);
      assertThat(build.totalSkillLevel(STAMINA_SURGE_ID))
        .as("Stamina Surge must be >= 3 in every build")
        .isGreaterThanOrEqualTo(3);
    }

    // 5. Top-1 free-slot score is at least 10200
    Build best = ranked.get(0);
    assertThat(best.freeSlotScore())
      .as("Top-1 free-slot score should be >= 10200 (was %d)", best.freeSlotScore())
      .isGreaterThanOrEqualTo(10200);

    // 6. Default config optimizes the bow kind
    assertThat(best.weapon().kind()).isEqualTo("bow");

    // Verify the ranking is strictly descending
    for (int i = 1; i < ranked.size(); i++) {
      assertThat(ranked.get(i - 1).freeSlotScore()).isGreaterThanOrEqualTo(
        ranked.get(i).freeSlotScore()
      );
    }
  }

  @Test
  void greatSwordConfigSolvesAndValidates() throws Exception {
    Path tmp = Files.createTempDirectory("gsw");
    Path cfg = tmp.resolve("gs.json");
    Files.writeString(
      cfg,
      """
      {
        "required_skills": { "-1689391744": 5, "-315492576": 3 },
        "weapon_type": "great-sword",
        "armor": { "exclude_sets": [], "exclude_skills": [] },
        "decorations": { "exclude_ids": [] },
        "amulets": { "exclude_families": [] },
        "weapons": { "include_ids": [] },
        "ignored_skills": [],
        "ranking": "free_slots"
      }
      """
    );

    GameData data = new GameDataLoader("data").load();
    GearPoolConfig config = GearPoolConfig.load(cfg.toString());
    SolverPool pool = new GearPoolResolver(data).resolve(config);
    List<Build> builds = new GreedySolver(100).solve(pool);

    assertThat(builds).isNotEmpty();
    assertThat(builds.get(0).weapon().kind()).isEqualTo("great-sword");

    BuildValidator validator = new BuildValidator(data.skills());

    for (Build b : builds) {
      assertThat(validator.validate(b)).as("build %s must validate", b).isEmpty();
    }
  }

  @Test
  void parallelSolverMatchesSequentialExactly() {
    GameData data = new GameDataLoader("data").load();
    GearPoolConfig config = GearPoolConfig.load("src/main/resources/default-config.json");
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    GreedySolver serial = new GreedySolver(1000, 1);
    List<Build> serialResults = serial.solve(pool);
    List<Build> serialRanked = serialResults.stream().sorted(ranking("free_slots")).toList();

    GreedySolver parallel = new GreedySolver(
      1000,
      Math.max(2, Runtime.getRuntime().availableProcessors())
    );
    List<Build> parallelResults = parallel.solve(pool);
    List<Build> parallelRanked = parallelResults.stream().sorted(ranking("free_slots")).toList();

    assertThat(parallelRanked).hasSize(serialRanked.size());

    for (int i = 0; i < serialRanked.size(); i++) {
      assertThat(parallelRanked.get(i).freeSlotScore())
        .as("score at rank %d must match serial", i)
        .isEqualTo(serialRanked.get(i).freeSlotScore());
    }

    assertThat(parallelRanked.get(0).freeSlotScore())
      .as("parallel top-1 must match serial top-1")
      .isEqualTo(serialRanked.get(0).freeSlotScore());
  }

  private static RankingStrategy ranking(String name) {
    return RankingFactory.create(name);
  }
}
