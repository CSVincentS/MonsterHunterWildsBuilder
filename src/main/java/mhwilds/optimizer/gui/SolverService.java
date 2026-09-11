package mhwilds.optimizer.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mhwilds.optimizer.config.GearPoolConfig;
import mhwilds.optimizer.config.GearPoolResolver;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.ranking.RankingFactory;
import mhwilds.optimizer.ranking.RankingStrategy;
import mhwilds.optimizer.solver.GreedySolver;
import mhwilds.optimizer.solver.SolverPool;

/** Pure solver front-end for the GUI; no Swing types. */
public class SolverService {

  private final GameData data;

  public SolverService(GameData data) {
    this.data = data;
  }

  public List<String> validate(List<SkillRequirement> requirements, RarityBounds bounds) {
    List<String> errors = new ArrayList<>();
    Map<String, Integer> names = skillNamesById();

    for (SkillRequirement req : requirements) {
      if (!isKnownSkill(req.skillName(), names)) {
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
    Map<String, Integer> required = new java.util.HashMap<>();

    for (SkillRequirement req : requirements) {
      required.put(req.skillName(), req.minLevel());
    }

    return new GearPoolConfig(
      Map.copyOf(required),
      weaponType,
      new GearPoolConfig.ArmorConfig(
        source.armor().excludeSets(),
        source.armor().excludeSkills(),
        bounds.armorMin(),
        bounds.armorMax()
      ),
      new GearPoolConfig.DecorationConfig(
        source.decorations().excludeIds(),
        bounds.decoMin(),
        bounds.decoMax()
      ),
      new GearPoolConfig.AmuletConfig(
        source.amulets().excludeFamilies(),
        bounds.amuletMin(),
        bounds.amuletMax()
      ),
      new GearPoolConfig.WeaponConfig(
        source.weapons().includeIds(),
        bounds.weaponMin(),
        bounds.weaponMax()
      ),
      source.ignoredSkills(),
      ranking != null ? ranking : source.ranking(),
      equipmentBonus != null ? equipmentBonus : source.equipmentSlotBonus()
    );
  }

  public SolveResult solve(GearPoolConfig config, int computeTopN, int showTopN) {
    long start = System.nanoTime();

    SolverPool pool = new GearPoolResolver(data).resolve(config);

    long bonus =
      config.equipmentSlotBonus() != null ? Math.max(0, config.equipmentSlotBonus()) : 0L;
    int threads = Runtime.getRuntime().availableProcessors();
    List<Build> all = new GreedySolver(computeTopN, threads, bonus).solve(pool);

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

  private Map<String, Integer> skillNamesById() {
    Map<String, Integer> names = new java.util.HashMap<>();

    for (Skill skill : data.skills().values()) {
      names.put(skill.name().toLowerCase(Locale.ROOT), skill.gameId());
    }

    return names;
  }

  private boolean isKnownSkill(String raw, Map<String, Integer> names) {
    if (names.containsKey(raw.toLowerCase(Locale.ROOT).trim())) {
      return true;
    }

    try {
      return data.skills().containsKey(Integer.parseInt(raw.trim()));
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private void checkInverted(List<String> errors, String category, Integer min, Integer max) {
    if (min != null && max != null && min > max) {
      errors.add(category + " min_rarity > max_rarity");
    }
  }
}
