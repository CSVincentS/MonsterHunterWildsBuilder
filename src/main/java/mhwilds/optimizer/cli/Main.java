package mhwilds.optimizer.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.config.GearPoolConfig;
import mhwilds.optimizer.config.GearPoolResolver;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.loader.GameDataLoader;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.SlotAssignment;
import mhwilds.optimizer.ranking.RankingFactory;
import mhwilds.optimizer.ranking.RankingStrategy;
import mhwilds.optimizer.solver.GreedySolver;
import mhwilds.optimizer.solver.SolverPool;

public class Main {

  private static final String DEFAULT_DATA_DIR = "data";
  private static final String DEFAULT_CONFIG = "src/main/resources/default-config.json";
  private static final int DEFAULT_TOP = 1000;
  private static final int DEFAULT_THREADS = Runtime.getRuntime().availableProcessors();

  public static void main(String[] args) {
    for (String arg : args) {
      if (arg.equals("--help") || arg.equals("-h")) {
        printUsage();

        return;
      }
    }

    CliArguments options;

    try {
      options = parseArgs(args);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      System.exit(1);

      return;
    }

    String dataDir = options.dataDir();
    String configPath = options.configPath();
    int topN = options.topN();
    String rankingName = options.ranking();
    String outputPath = options.outputPath();

    PrintStream out;

    if (outputPath != null) {
      try {
        out = new PrintStream(Files.newOutputStream(Path.of(outputPath)));
      } catch (IOException e) {
        System.err.println("Failed to open output file: " + e.getMessage());
        System.exit(1);

        return;
      }
    } else {
      out = System.out;
    }

    System.err.println("Loading game data from " + dataDir + "...");
    GameData data = new GameDataLoader(dataDir).load();
    System.err.println(
      "Loaded: " +
        data.skills().size() +
        " skills, " +
        data.armorPieces().size() +
        " armor pieces, " +
        data.decorations().size() +
        " decorations, " +
        data.amuletRanks().size() +
        " amulet ranks, " +
        data.weapons().size() +
        " weapons"
    );

    System.err.println("Resolving gear pool from " + configPath + "...");
    GearPoolConfig config = GearPoolConfig.load(configPath);
    GearPoolResolver resolver = new GearPoolResolver(data);
    SolverPool pool = resolver.resolve(config);

    System.err.println("Solving...");
    long start = System.currentTimeMillis();
    String strategyName = rankingName != null ? rankingName : config.ranking();
    long equipmentBonus =
      options.equipmentBonus() != null
        ? options.equipmentBonus()
        : config.equipmentSlotBonus() != null
          ? config.equipmentSlotBonus()
          : 0L;
    GreedySolver solver = new GreedySolver(topN, options.threads(), equipmentBonus);
    List<Build> results = solver.solve(pool);
    long elapsed = System.currentTimeMillis() - start;
    System.err.println("Solver found " + results.size() + " builds in " + elapsed + "ms");

    if (results.isEmpty()) {
      out.println("No valid builds found.");

      if (outputPath != null) {
        out.close();
      }

      return;
    }

    RankingStrategy ranking = RankingFactory.create(strategyName, equipmentBonus);
    Integer showOpt = options.showTopN();
    int showN = showOpt != null ? showOpt : topN;
    List<Build> ranked = results.stream().sorted(ranking).limit(showN).toList();
    boolean equipmentSlotRanking = RankingFactory.isEquipmentSlotRanking(strategyName);

    out.println("=== Top " + ranked.size() + " Builds (ranking: " + strategyName + ") ===");
    out.println();
    Map<Integer, Skill> skillMap = pool.skillMap();

    for (int i = 0; i < ranked.size(); i++) {
      printBuild(out, i + 1, ranked.get(i), skillMap, equipmentSlotRanking, equipmentBonus);
    }

    if (outputPath != null) {
      out.close();
      System.err.println("Output written to " + outputPath);
    }
  }

  public record CliArguments(
    String dataDir,
    String configPath,
    int topN,
    Integer showTopN,
    String ranking,
    String outputPath,
    int threads,
    Long equipmentBonus
  ) {}

  public static CliArguments parseArgs(String[] args) {
    String dataDir = DEFAULT_DATA_DIR;
    String configPath = DEFAULT_CONFIG;
    int topN = DEFAULT_TOP;
    Integer showTopN = null;
    String rankingName = null;
    String outputPath = null;
    int threads = DEFAULT_THREADS;
    Long equipmentBonus = null;
    boolean dataDirConsumed = false;
    boolean configPathConsumed = false;

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--top" -> {
          topN = parseIntValue(args, ++i, "--top");
          if (topN < 1) {
            throw new IllegalArgumentException("--top must be >= 1, got " + topN);
          }
        }
        case "--show" -> {
          int s = parseIntValue(args, ++i, "--show");
          if (s < 1) {
            throw new IllegalArgumentException("--show must be >= 1, got " + s);
          }
          showTopN = s;
        }
        case "--threads" -> {
          int t = parseIntValue(args, ++i, "--threads");
          if (t < 1) {
            throw new IllegalArgumentException("--threads must be >= 1, got " + t);
          }
          threads = t;
        }
        case "--ranking" -> {
          rankingName = stringValue(args, ++i, "--ranking");
        }
        case "--equipment-bonus" -> {
          long v = parseIntValue(args, ++i, "--equipment-bonus");
          if (v < 0) {
            throw new IllegalArgumentException("--equipment-bonus must be >= 0, got " + v);
          }
          equipmentBonus = v;
        }
        case "--output" -> {
          outputPath = stringValue(args, ++i, "--output");
        }
        default -> {
          // Positional arguments are consumed in order: data-dir first, then config path.
          if (args[i].startsWith("-")) {
            throw new IllegalArgumentException("Unknown option: " + args[i]);
          }

          if (!dataDirConsumed) {
            dataDir = args[i];
            dataDirConsumed = true;
          } else if (!configPathConsumed) {
            configPath = args[i];
            configPathConsumed = true;
          } else {
            throw new IllegalArgumentException("Unexpected argument: " + args[i]);
          }
        }
      }
    }

    return new CliArguments(
      dataDir,
      configPath,
      topN,
      showTopN,
      rankingName,
      outputPath,
      threads,
      equipmentBonus
    );
  }

  private static String stringValue(String[] args, int i, String option) {
    if (i >= args.length) {
      throw new IllegalArgumentException(option + " requires a value");
    }

    return args[i];
  }

  private static int parseIntValue(String[] args, int i, String option) {
    String value = stringValue(args, i, option);

    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(option + " requires an integer, got " + value);
    }
  }

  private static void printBuild(
    PrintStream out,
    int rank,
    Build build,
    Map<Integer, Skill> skillMap,
    boolean equipmentSlotRanking,
    long equipmentBonus
  ) {
    if (equipmentSlotRanking) {
      out.printf(
        "#%d  Equipment-slot score: %d  (free slots %d, " +
          (equipmentBonus > 0 ? "%d omitted x %d bonus" : "%d omitted") +
          ")%n",
        rank,
        build.equipmentAwareScore(equipmentBonus),
        build.freeSlotScore(),
        build.omittedEquipmentCount(),
        equipmentBonus
      );
    } else {
      out.printf("#%d  Free-slot score: %d%n", rank, build.freeSlotScore());
    }

    out.print("  Armor:");
    for (ArmorSlot slot : ArmorSlot.values()) {
      ArmorPiece piece = build.armorPieces()[slot.ordinal()];
      String name = piece != null ? piece.setName() : "none";
      out.printf(" %s=%s", slot.name().toLowerCase(), name);
    }
    out.println();

    out.print("  Decorations:");
    if (build.armorDecorations().isEmpty() && build.weaponDecorations().isEmpty()) {
      out.print(" (none)");
    } else {
      for (SlotAssignment sa : build.armorDecorations()) {
        String target = sa.targetPiece() != null ? sa.targetPiece().name().toLowerCase() : "weapon";
        out.printf(" %s->%s", sa.decoration().name(), target);
      }
      for (SlotAssignment sa : build.weaponDecorations()) {
        out.printf(" %s->weapon", sa.decoration().name());
      }
    }
    out.println();

    out.printf("  Amulet: %s%n", build.amuletRank() != null ? build.amuletRank().name() : "none");
    out.printf("  Weapon: %s%n", build.weapon() != null ? build.weapon().name() : "none");

    out.print("  Skills:");
    Map<Integer, Integer> skills = build.combinedSkills();
    if (skills.isEmpty()) {
      out.print(" (none)");
    } else {
      for (var entry : skills.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
        Skill skill = skillMap.get(entry.getKey());
        String name = skill != null ? skill.name() : String.valueOf(entry.getKey());
        int level = entry.getValue();

        if (skill != null && level > skill.maxRank()) {
          level = skill.maxRank();
        }
        out.printf(" %s=%d", name, level);
      }
    }
    out.println();
    out.println();
  }

  private static void printUsage() {
    System.out.println("Usage: mh-wilds-optimizer [options] [data-dir] [config-path]");
    System.out.println();
    System.out.println("Arguments:");
    System.out.println(
      "  data-dir           Game data directory (default: " + DEFAULT_DATA_DIR + ")"
    );
    System.out.println("  config-path        Config JSON file (default: " + DEFAULT_CONFIG + ")");
    System.out.println();
    System.out.println("Options:");
    System.out.println(
      "  --top N            Number of top builds to compute (default: " + DEFAULT_TOP + ")"
    );
    System.out.println(
      "  --show N           Number of computed builds to display (default: all computed)"
    );
    System.out.println(
      "  --threads N        Number of solver threads (default: " + DEFAULT_THREADS + ")"
    );
    System.out.println(
      "  --ranking <name>   Ranking strategy: free_slots (default) or free_equipment_slots"
    );
    System.out.println(
      "  --equipment-bonus <n>  Points awarded per omitted equipment slot, e.g. 1000 = one"
    );
    System.out.println(
      "                    free level-3 decoration slot. Used by free_equipment_slots ranking."
    );
    System.out.println("  --output <file>    Write output to file instead of stdout");
    System.out.println("  -h, --help         Show this help message");
  }
}
