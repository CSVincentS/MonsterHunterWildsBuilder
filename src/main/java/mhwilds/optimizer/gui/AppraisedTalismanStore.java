package mhwilds.optimizer.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.loader.GameData;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.AppraisedTalisman;

public final class AppraisedTalismanStore {

  public static final String DEFAULT_FILE_NAME = "appraised_talismans.json";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private boolean enabled = true;
  private final List<AppraisedTalisman> talismans = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public List<AppraisedTalisman> talismans() {
    return Collections.unmodifiableList(talismans);
  }

  public void add(AppraisedTalisman talisman) {
    if (talisman == null) {
      throw new NullPointerException("talisman must not be null");
    }

    talismans.add(talisman);
  }

  public void remove(int index) {
    talismans.remove(index);
  }

  public void clear() {
    talismans.clear();
  }

  public List<AmuletRank> toAmuletRanks() {
    List<AmuletRank> ranks = new ArrayList<>(talismans.size());

    for (int i = 0; i < talismans.size(); i++) {
      ranks.add(talismans.get(i).toAmuletRank(i));
    }

    return ranks;
  }

  public static Path defaultPath() {
    return Paths.get(DEFAULT_FILE_NAME);
  }

  public static AppraisedTalismanStore loadDefault(GameData data) {
    return load(defaultPath(), data);
  }

  public static AppraisedTalismanStore load(Path path, GameData data) {
    AppraisedTalismanStore store = new AppraisedTalismanStore();

    if (!Files.isRegularFile(path)) {
      return store;
    }

    JsonNode root;

    try {
      root = MAPPER.readTree(Files.readString(path));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read appraised talismans from " + path, e);
    }

    if (root.has("enabled")) {
      store.enabled = root.get("enabled").asBoolean(true);
    }

    JsonNode list = root.has("talismans") ? root.get("talismans") : root;

    if (!list.isArray()) {
      System.err.println(
        "Warning: ignoring " + path + ": expected an object with a talismans array"
      );

      return store;
    }

    int skipped = 0;

    for (JsonNode node : list) {
      try {
        store.talismans.add(parseEntry(node, data));
      } catch (IllegalArgumentException e) {
        skipped++;
        System.err.println("Warning: ignoring invalid appraised talisman: " + e.getMessage());
      }
    }

    if (skipped > 0) {
      System.err.println(
        "Warning: skipped " + skipped + " invalid appraised talisman(s) in " + path
      );
    }

    return store;
  }

  public void saveDefault() {
    save(defaultPath());
  }

  public void save(Path path) {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("enabled", enabled);

    ArrayNode list = root.putArray("talismans");

    for (AppraisedTalisman talisman : talismans) {
      ObjectNode node = list.addObject();
      node.put("name", talisman.name());
      node.put("rarity", talisman.rarity());

      ObjectNode skills = node.putObject("skills");

      for (Map.Entry<Integer, Integer> entry : talisman.skills().entrySet()) {
        skills.put(String.valueOf(entry.getKey()), entry.getValue());
      }

      ArrayNode armor = node.putArray("armor_slots");

      for (int size : talisman.armorSlots()) {
        armor.add(size);
      }

      ArrayNode weapon = node.putArray("weapon_slots");

      for (int size : talisman.weaponSlots()) {
        weapon.add(size);
      }
    }

    try {
      Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to save appraised talismans to " + path, e);
    }
  }

  private static AppraisedTalisman parseEntry(JsonNode node, GameData data) {
    if (!node.isObject()) {
      throw new IllegalArgumentException("entry is not an object");
    }

    JsonNode nameNode = node.get("name");
    String name = nameNode != null && !nameNode.isNull() ? nameNode.asText() : "";

    if (name.isBlank()) {
      throw new IllegalArgumentException("entry has no name");
    }

    int rarity = node.has("rarity") ? node.get("rarity").asInt(-1) : -1;

    Map<Integer, Integer> skills = new LinkedHashMap<>();
    JsonNode skillsNode = node.get("skills");

    if (skillsNode == null || !skillsNode.isObject() || skillsNode.isEmpty()) {
      throw new IllegalArgumentException("'" + name + "' has no skills");
    }

    for (Map.Entry<String, JsonNode> entry : skillsNode.properties()) {
      int skillId;

      try {
        skillId = Integer.parseInt(entry.getKey());
      } catch (NumberFormatException e) {
        Integer resolved = data.resolveSkillRef(entry.getKey());

        if (resolved == null) {
          throw new IllegalArgumentException(
            "'" + name + "' references unknown skill '" + entry.getKey() + "'"
          );
        }

        skillId = resolved;
      }

      var skill = data.skills().get(skillId);

      if (skill == null) {
        throw new IllegalArgumentException("'" + name + "' references unknown skill id " + skillId);
      }

      int level = entry.getValue().asInt(-1);

      if (level < 1 || level > skill.maxRank()) {
        throw new IllegalArgumentException(
          "'" +
            name +
            "' has invalid level " +
            level +
            " for skill " +
            skill.name() +
            " (max " +
            skill.maxRank() +
            ")"
        );
      }

      if (skills.containsKey(skillId)) {
        throw new IllegalArgumentException("'" + name + "' lists skill " + skill.name() + " twice");
      }

      skills.put(skillId, level);
    }

    return new AppraisedTalisman(
      name,
      rarity,
      skills,
      intList(node.get("armor_slots"), "'" + name + "'"),
      intList(node.get("weapon_slots"), "'" + name + "'")
    );
  }

  private static int[] intList(JsonNode node, String owner) {
    if (node == null || node.isNull()) {
      return new int[0];
    }

    if (!node.isArray()) {
      throw new IllegalArgumentException(owner + " has non-array slots");
    }

    int[] result = new int[node.size()];

    for (int i = 0; i < result.length; i++) {
      result[i] = node.get(i).asInt(-1);

      if (result[i] < 0 || result[i] > AppraisedTalisman.MAX_SLOT_SIZE) {
        throw new IllegalArgumentException(owner + " has invalid slot size " + result[i]);
      }
    }

    return result;
  }
}
