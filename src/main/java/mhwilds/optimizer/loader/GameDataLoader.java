package mhwilds.optimizer.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Decoration;
import mhwilds.optimizer.model.Skill;
import mhwilds.optimizer.model.SlotTarget;
import mhwilds.optimizer.model.Weapon;

/**
 * Loads the game data snapshot from {@code data/} into model objects. Skill dictionaries carry
 * skill ids as string keys (e.g. {@code {"-1689391744": 2}}); these are parsed to int. Armor sets
 * are flattened into individual pieces and amulet families into individual ranks. Every skill
 * referenced by a loaded source must exist in Skill.json, otherwise loading fails with a clear
 * message.
 */
public class GameDataLoader {

  /** Weapon JSON keys consumed by the optimizer; everything else lands in extraFields. */
  private static final Set<String> WEAPON_CORE_KEYS = Set.of(
    "game_id",
    "names",
    "descriptions",
    "kind",
    "rarity",
    "attack_raw",
    "affinity",
    "defense",
    "slots",
    "specials",
    "crafting",
    "skills",
    "series_id"
  );

  private final Path dataDir;
  private final ObjectMapper mapper = new ObjectMapper();

  public GameDataLoader(String dataDir) {
    this(Paths.get(dataDir));
  }

  public GameDataLoader(Path dataDir) {
    if (dataDir == null) {
      throw new NullPointerException("dataDir must not be null");
    }

    this.dataDir = dataDir;
  }

  public GameData load() {
    if (!Files.isDirectory(dataDir)) {
      throw new IllegalArgumentException(
        "Game data directory does not exist: " + dataDir.toAbsolutePath()
      );
    }

    Map<Integer, Skill> skills = loadSkills();

    List<ArmorPiece> armorPieces = loadArmorPieces();

    List<Decoration> decorations = loadDecorations();

    List<AmuletRank> amuletRanks = loadAmuletRanks();

    List<Weapon> weapons = loadWeapons();

    Map<Integer, Map<Integer, Integer>> setSkillRanks = collectSetSkillRanks(armorPieces);

    GameData data = new GameData(
      skills,
      armorPieces,
      decorations,
      amuletRanks,
      weapons,
      setSkillRanks
    );

    validateSkillReferences(data);

    return data;
  }

  /**
   * Maps every set/group bonus skill id to its activation thresholds ({@code {piecesRequired:
   * skillLevel}}). Skill id == bonus id (verified across data).
   */
  private static Map<Integer, Map<Integer, Integer>> collectSetSkillRanks(
    List<ArmorPiece> armorPieces
  ) {
    Map<Integer, Map<Integer, Integer>> ranks = new HashMap<>();

    for (ArmorPiece piece : armorPieces) {
      if (piece.setBonusId() != 0) {
        ranks
          .computeIfAbsent(piece.setBonusId(), id -> new HashMap<>())
          .putAll(piece.setBonusRanks());
      }
      if (piece.groupBonusId() != 0) {
        ranks
          .computeIfAbsent(piece.groupBonusId(), id -> new HashMap<>())
          .putAll(piece.groupBonusRanks());
      }
    }

    // A zero-piece entry is never an activation threshold (0 pieces would mean "always active").

    for (Map<Integer, Integer> map : ranks.values()) {
      map.remove(0);
    }

    Map<Integer, Map<Integer, Integer>> copy = new HashMap<>();

    ranks.forEach((id, map) -> copy.put(id, Map.copyOf(map)));

    return Map.copyOf(copy);
  }

  private Map<Integer, Skill> loadSkills() {
    Map<Integer, Skill> skills = new HashMap<>();

    for (JsonNode node : read("Skill.json")) {
      int gameId = node.get("game_id").asInt();
      String name = enName(node);
      int maxRank = node.get("ranks").size();

      if (name == null || name.isBlank()) {
        throw new IllegalStateException("Skill " + gameId + " has no English name");
      }

      skills.put(gameId, new Skill(gameId, name, maxRank));
    }

    return Map.copyOf(skills);
  }

  private List<ArmorPiece> loadArmorPieces() {
    List<ArmorPiece> pieces = new ArrayList<>();

    for (JsonNode set : read("Armor.json")) {
      int setId = set.get("game_id").asInt();
      String setName = enName(set);
      int setRarity = set.get("rarity").asInt();
      int setBonusId = bonusId(set, "set_bonus", "set_bonus_id");
      Map<Integer, Integer> setBonusRanks = bonusRanks(set.get("set_bonus"));
      int groupBonusId = bonusId(set, "group_bonus", "group_bonus_id");
      Map<Integer, Integer> groupBonusRanks = bonusRanks(set.get("group_bonus"));

      for (JsonNode piece : set.get("pieces")) {
        ArmorSlot kind = ArmorSlot.fromString(piece.get("kind").asText());
        JsonNode defense = piece.get("defense");
        pieces.add(
          new ArmorPiece(
            setId,
            setName,
            kind,
            intArray(piece.get("slots")),
            parseSkills(piece.get("skills")),
            defense.get("base").asInt(),
            defense.get("max").asInt(),
            setRarity,
            setBonusId,
            setBonusRanks,
            groupBonusId,
            groupBonusRanks
          )
        );
      }
    }

    return pieces;
  }

  /** Set/group bonus skill id from the bonus object (preferred) or the bare id field. */
  private static int bonusId(JsonNode set, String objField, String idField) {
    JsonNode obj = set.get(objField);

    if (obj != null && !obj.isNull() && obj.has("skill_id")) {
      return obj.get("skill_id").asInt();
    }

    JsonNode id = set.get(idField);

    return id == null || id.isNull() ? 0 : id.asInt();
  }

  /** Rank thresholds for a bonus: {@code {piecesRequired: skillLevel}}. */
  private static Map<Integer, Integer> bonusRanks(JsonNode bonus) {
    if (bonus == null || bonus.isNull() || !bonus.has("ranks")) {
      return Map.of();
    }

    Map<Integer, Integer> ranks = new HashMap<>();

    for (JsonNode rank : bonus.get("ranks")) {
      ranks.put(rank.get("pieces").asInt(), rank.get("skill_level").asInt());
    }

    return Map.copyOf(ranks);
  }

  private List<Decoration> loadDecorations() {
    List<Decoration> decorations = new ArrayList<>();

    for (JsonNode node : read("Accessory.json")) {
      decorations.add(
        new Decoration(
          node.get("game_id").asInt(),
          enName(node),
          node.get("level").asInt(),
          parseSkills(node.get("skills")),
          SlotTarget.fromString(node.get("allowed_on").asText()),
          node.get("rarity").asInt()
        )
      );
    }

    return decorations;
  }

  private List<AmuletRank> loadAmuletRanks() {
    List<AmuletRank> ranks = new ArrayList<>();

    for (JsonNode family : read("Amulet.json")) {
      int familyGameId = family.get("game_id").asInt();

      for (JsonNode rank : family.get("ranks")) {
        ranks.add(
          new AmuletRank(
            familyGameId,
            rank.get("level").asInt(),
            enName(rank),
            parseSkills(rank.get("skills")),
            rank.get("rarity").asInt()
          )
        );
      }
    }

    return ranks;
  }

  private List<Weapon> loadWeapons() {
    List<Weapon> weapons = new ArrayList<>();

    // Weapon data is one file per weapon type, unlike the other single-file categories.
    Path weaponsDir = dataDir.resolve("weapons");

    if (!Files.isDirectory(weaponsDir)) {
      throw new UncheckedIOException(new IOException("weapons directory missing: " + weaponsDir));
    }

    List<Path> files;

    try (var stream = Files.list(weaponsDir)) {
      files = stream
        .filter(p -> p.getFileName().toString().endsWith(".json"))
        .sorted()
        .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to list weapon files: " + weaponsDir, e);
    }

    for (Path file : files) {
      JsonNode root;

      try (InputStream in = Files.newInputStream(file)) {
        root = mapper.readTree(in);
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read weapon file: " + file, e);
      }

      for (JsonNode node : root) {
        weapons.add(parseWeapon(node));
      }
    }

    return weapons;
  }

  private Weapon parseWeapon(JsonNode node) {
    Map<String, Object> extras = new LinkedHashMap<>();

    var fields = node.fields();

    while (fields.hasNext()) {
      var entry = fields.next();

      if (WEAPON_CORE_KEYS.contains(entry.getKey())) {
        continue;
      }

      Object value = mapper.convertValue(entry.getValue(), Object.class);

      if (value == null) {
        continue;
      }

      extras.put(entry.getKey(), value);
    }

    return new Weapon(
      node.get("game_id").asInt(),
      node.get("kind").asText(),
      enName(node),
      node.get("attack_raw").asInt(),
      node.get("affinity").asInt(),
      intArray(node.get("slots")),
      parseSkills(node.get("skills")),
      stringList(node.get("coatings")),
      Map.copyOf(extras),
      node.get("rarity").asInt()
    );
  }

  private void validateSkillReferences(GameData data) {
    StringBuilder orphans = new StringBuilder();

    for (ArmorPiece piece : data.armorPieces()) {
      for (int skillId : piece.skills().keySet()) {
        if (!data.skills().containsKey(skillId)) {
          orphans
            .append("\n  armor piece ")
            .append(piece.gameId())
            .append(" references unknown skill ")
            .append(skillId);
        }
      }
    }

    for (Decoration decoration : data.decorations()) {
      for (int skillId : decoration.skills().keySet()) {
        if (!data.skills().containsKey(skillId)) {
          orphans
            .append("\n  decoration ")
            .append(decoration.gameId())
            .append(" references unknown skill ")
            .append(skillId);
        }
      }
    }

    for (AmuletRank rank : data.amuletRanks()) {
      for (int skillId : rank.skills().keySet()) {
        if (!data.skills().containsKey(skillId)) {
          orphans
            .append("\n  amulet rank ")
            .append(rank.familyGameId())
            .append(" references unknown skill ")
            .append(skillId);
        }
      }
    }

    for (Weapon weapon : data.weapons()) {
      for (int skillId : weapon.skills().keySet()) {
        if (!data.skills().containsKey(skillId)) {
          orphans
            .append("\n  weapon ")
            .append(weapon.gameId())
            .append(" references unknown skill ")
            .append(skillId);
        }
      }
    }

    if (!orphans.isEmpty()) {
      throw new IllegalStateException(
        "Game data references skills not present in Skill.json:" + orphans
      );
    }
  }

  private Map<Integer, Integer> parseSkills(JsonNode skillsNode) {
    if (skillsNode == null || skillsNode.isNull()) {
      return Map.of();
    }

    Map<Integer, Integer> parsed = new HashMap<>();

    skillsNode
      .fields()
      .forEachRemaining(entry ->
        parsed.put(Integer.parseInt(entry.getKey()), entry.getValue().asInt())
      );

    return Map.copyOf(parsed);
  }

  private static String enName(JsonNode node) {
    JsonNode names = node.get("names");

    if (names == null || names.isNull()) {
      return null;
    }

    JsonNode en = names.get("en");

    return en == null || en.isNull() ? null : en.asText();
  }

  private static int[] intArray(JsonNode node) {
    if (node == null || node.isNull()) {
      return new int[0];
    }

    int[] result = new int[node.size()];

    for (int i = 0; i < result.length; i++) {
      result[i] = node.get(i).asInt();
    }

    return result;
  }

  private static List<String> stringList(JsonNode node) {
    if (node == null || node.isNull()) {
      return List.of();
    }

    List<String> result = new ArrayList<>(node.size());

    node.forEach(item -> result.add(item.asText()));

    return List.copyOf(result);
  }

  private JsonNode read(String relativePath) {
    Path file = dataDir.resolve(relativePath);

    try (InputStream in = Files.newInputStream(file)) {
      return mapper.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read game data file: " + file, e);
    }
  }
}
