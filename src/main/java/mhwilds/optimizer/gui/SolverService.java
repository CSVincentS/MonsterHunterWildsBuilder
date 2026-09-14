package mhwilds.optimizer.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.config.GearPoolConfig;
import mhwilds.optimizer.config.GearPoolResolver;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.ranking.RankingFactory;
import mhwilds.optimizer.ranking.RankingStrategy;
import mhwilds.optimizer.solver.CatalogSolver;
import mhwilds.optimizer.solver.SolverPool;

public class SolverService {

  private final GameData data;

  public SolverService(GameData data) {
    this.data = data;
  }

  public List<String> validate(List<SkillRequirement> requirements, RarityBounds bounds) {
    List<String> errors = new ArrayList<>();

    for (SkillRequirement req : requirements) {
      if (data.resolveSkillRef(req.skillName()) == null) {
        errors.add("Unknown skill in requirements: " + req.skillName());
      }
    }

    checkInverted(errors, "armor", bounds.armorMin(), bounds.armorMax());
    checkInverted(errors, "decorations", bounds.decoMin(), bounds.decoMax());
    checkInverted(errors, "amulets", bounds.amuletMin(), bounds.amuletMax());
    checkInverted(errors, "weapons", bounds.weaponMin(), bounds.weaponMax());

    return errors;
  }

  public GearPoolConfig buildConfig(
    GearPoolConfig source,
    String weaponType,
    List<SkillRequirement> requirements,
    RarityBounds bounds,
    String ranking,
    Long equipmentBonus
  ) {
    Map<String, Integer> required = new HashMap<>();

    for (SkillRequirement req : requirements) {
      required.put(req.skillName(), req.minLevel());
    }

    return source
      .withRequiredSkills(required)
      .withWeaponType(weaponType)
      .withArmor(
        new GearPoolConfig.ArmorConfig(
          source.armor().excludeSets(),
          source.armor().excludeSkills(),
          bounds.armorMin(),
          bounds.armorMax()
        )
      )
      .withDecorations(
        new GearPoolConfig.DecorationConfig(
          source.decorations().excludeIds(),
          bounds.decoMin(),
          bounds.decoMax()
        )
      )
      .withAmulets(
        new GearPoolConfig.AmuletConfig(
          source.amulets().excludeFamilies(),
          bounds.amuletMin(),
          bounds.amuletMax()
        )
      )
      .withWeapons(
        new GearPoolConfig.WeaponConfig(
          source.weapons().includeIds(),
          bounds.weaponMin(),
          bounds.weaponMax()
        )
      )
      .withRanking(ranking != null ? ranking : source.ranking())
      .withEquipmentSlotBonus(
        equipmentBonus != null ? equipmentBonus : source.equipmentSlotBonus()
      );
  }

  public SolveResult solve(GearPoolConfig config, int computeTopN, int showTopN) {
    long start = System.nanoTime();

    SolverPool pool = new GearPoolResolver(data).resolve(config);

    long bonus =
      config.equipmentSlotBonus() != null ? Math.max(0, config.equipmentSlotBonus()) : 0L;
    List<Build> all = new CatalogSolver(computeTopN, bonus).solve(pool);

    RankingStrategy ranking = RankingFactory.create(config.ranking(), bonus);
    List<Build> ranked = all.stream().sorted(ranking).limit(Math.max(0, showTopN)).toList();

    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    return new SolveResult(ranked, all.size(), elapsedMillis, pool.skillMap());
  }

  public record SolveResult(
    List<Build> builds,
    int computedCount,
    long elapsedMillis,
    Map<Integer, Skill> skillMap
  ) {}

  private void checkInverted(List<String> errors, String category, Integer min, Integer max) {
    if (min != null && max != null && min > max) {
      errors.add(category + " min_rarity > max_rarity");
    }
  }
}
