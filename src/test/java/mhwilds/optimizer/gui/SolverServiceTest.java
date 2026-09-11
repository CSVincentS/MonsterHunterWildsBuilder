package mhwilds.optimizer.gui;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import mhwilds.optimizer.config.GearPoolConfig;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.loader.GameDataLoader;
import mhwilds.optimizer.model.*;
import org.junit.jupiter.api.Test;

class SolverServiceTest {

  private final GameData data = new GameDataLoader("data").load();

  @Test
  void validateAcceptsKnownSkillNames() {
    SolverService service = new SolverService(data);

    assertThat(
      service.validate(List.of(new SkillRequirement("constitution", 3)), RarityBounds.unbounded())
    ).isEmpty();
  }

  @Test
  void validateRejectsUnknownSkillName() {
    SolverService service = new SolverService(data);

    assertThat(
      service.validate(List.of(new SkillRequirement("NotASkill", 1)), RarityBounds.unbounded())
    ).containsExactly("Unknown skill in requirements: NotASkill");
  }

  @Test
  void validateAcceptsNumericSkillIds() {
    SolverService service = new SolverService(data);

    assertThat(
      service.validate(List.of(new SkillRequirement("-1689391744", 3)), RarityBounds.unbounded())
    ).isEmpty();
  }

  @Test
  void validateRejectsInvertedRarity() {
    SolverService service = new SolverService(data);

    assertThat(
      service.validate(List.of(), new RarityBounds(7, 3, null, null, null, null, null, null))
    ).containsExactly("armor min_rarity > max_rarity");
  }

  @Test
  void buildConfigPreservesPassThroughFields() {
    SolverService service = new SolverService(data);
    GearPoolConfig source = GearPoolConfig.load("src/main/resources/default-config.json");
    GearPoolConfig merged = service.buildConfig(
      source,
      "bow",
      List.of(new SkillRequirement("burst", 1)),
      new RarityBounds(4, null, null, null, null, null, null, null)
    );

    assertThat(merged.requiredSkills()).containsExactly(java.util.Map.entry("burst", 1));
    assertThat(merged.armor().minRarity()).isEqualTo(4);
    assertThat(merged.ignoredSkills()).isEqualTo(source.ignoredSkills());
    assertThat(merged.weaponType()).isEqualTo("bow");
    assertThat(merged.weapons().includeIds()).isEqualTo(source.weapons().includeIds());
    assertThat(merged.ranking()).isEqualTo(source.ranking());
  }

  @Test
  void buildConfigCarriesWeaponType() {
    SolverService service = new SolverService(data);
    GearPoolConfig merged = service.buildConfig(
      GearPoolConfig.load("src/main/resources/default-config.json"),
      "great-sword",
      List.of(),
      RarityBounds.unbounded()
    );

    assertThat(merged.weaponType()).isEqualTo("great-sword");
  }

  @Test
  void solveRanksByFreeSlotsAndLimitsTopN() {
    SolverService service = new SolverService(data);
    GearPoolConfig config = service.buildConfig(
      GearPoolConfig.load("src/main/resources/default-config.json"),
      "bow",
      List.of(new SkillRequirement("constitution", 5), new SkillRequirement("stamina surge", 3)),
      RarityBounds.unbounded()
    );
    var result = service.solve(config, 3, 3);

    assertThat(result.builds()).hasSize(3);
    assertThat(result.computedCount()).isEqualTo(3);
    assertThat(result.builds())
      .extracting(Build::freeSlotScore)
      .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    assertThat(result.skillMap()).isNotEmpty();
  }

  @Test
  void solveComputesMoreThanItShows() {
    SolverService service = new SolverService(data);
    GearPoolConfig config = service.buildConfig(
      GearPoolConfig.load("src/main/resources/default-config.json"),
      "bow",
      List.of(new SkillRequirement("constitution", 5), new SkillRequirement("stamina surge", 3)),
      RarityBounds.unbounded()
    );
    var result = service.solve(config, 10, 3);

    assertThat(result.computedCount()).isEqualTo(10);
    assertThat(result.builds()).hasSize(3);
    assertThat(result.builds())
      .extracting(Build::freeSlotScore)
      .isSortedAccordingTo(java.util.Comparator.reverseOrder());
  }
}
