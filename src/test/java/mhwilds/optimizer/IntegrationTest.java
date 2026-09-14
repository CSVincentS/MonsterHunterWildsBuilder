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
import mhwilds.optimizer.solver.CatalogSolver;
import mhwilds.optimizer.solver.SolverPool;
import mhwilds.optimizer.validity.BuildValidator;
import org.junit.jupiter.api.Test;

class IntegrationTest {

  private static final int CONSTITUTION_ID = -1689391744;
  private static final int STAMINA_SURGE_ID = -315492576;

  @Test
  void endToEndPipelineProducesValidTopKBuilds() {
    long start = System.currentTimeMillis();

    GameData data = new GameDataLoader("data").load();
    GearPoolConfig config = GearPoolConfig.load("src/main/resources/default-config.json");
    SolverPool pool = new GearPoolResolver(data).resolve(config);

    CatalogSolver solver = new CatalogSolver();
    List<Build> results = solver.solve(pool);

    long elapsed = System.currentTimeMillis() - start;

    assertThat(elapsed)
      .as("Solver should complete within 30 seconds (was %d ms)", elapsed)
      .isLessThan(30_000);

    assertThat(results)
      .as("Solver should find at least 1000 builds, found %d", results.size())
      .hasSizeGreaterThanOrEqualTo(1000);

    // Rank all results so we can validate the full set
    RankingStrategy ranking = RankingFactory.create("free_slots");
    List<Build> ranked = results.stream().sorted(ranking).toList();
    assertThat(ranked).hasSizeGreaterThanOrEqualTo(1000);

    BuildValidator validator = new BuildValidator(pool.skillMap());
    long invalidCount = ranked
      .stream()
      .map(validator::validate)
      .filter(errors -> !errors.isEmpty())
      .count();
    assertThat(invalidCount)
      .as("All builds must pass BuildValidator (found %d invalid)", invalidCount)
      .isZero();

    for (Build build : ranked) {
      assertThat(build.totalSkillLevel(CONSTITUTION_ID))
        .as("Constitution must be >= 5 in every build")
        .isGreaterThanOrEqualTo(5);
      assertThat(build.totalSkillLevel(STAMINA_SURGE_ID))
        .as("Stamina Surge must be >= 3 in every build")
        .isGreaterThanOrEqualTo(3);
    }

    Build best = ranked.get(0);
    assertThat(best.freeSlotScore())
      .as("Top-1 free-slot score should be >= 10200 (was %d)", best.freeSlotScore())
      .isGreaterThanOrEqualTo(10200);

    assertThat(best.weapon().kind()).isEqualTo("bow");

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
    List<Build> builds = new CatalogSolver(100).solve(pool);

    assertThat(builds).isNotEmpty();
    assertThat(builds.get(0).weapon().kind()).isEqualTo("great-sword");

    BuildValidator validator = new BuildValidator(data.skills());

    for (Build b : builds) {
      assertThat(validator.validate(b)).as("build %s must validate", b).isEmpty();
    }
  }
}
