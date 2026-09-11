package mhwilds.optimizer.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GearPoolConfig(
  // Keys stay strings: skill game_ids are large signed ints that lose precision as JSON
  // numbers, so keys arrive as either a numeric id or an English skill name.
  @JsonProperty("required_skills") Map<String, Integer> requiredSkills,
  @JsonProperty("weapon_type") String weaponType,
  @JsonProperty("armor") ArmorConfig armor,
  @JsonProperty("decorations") DecorationConfig decorations,
  @JsonProperty("amulets") AmuletConfig amulets,
  @JsonProperty("weapons") WeaponConfig weapons,
  @JsonProperty("ignored_skills") java.util.List<Integer> ignoredSkills,
  @JsonProperty("ranking") String ranking,
  @JsonProperty("equipment_slot_bonus") Long equipmentSlotBonus
) {
  public GearPoolConfig {
    if (requiredSkills == null) {
      requiredSkills = Map.of();
    }

    if (weaponType == null || weaponType.isBlank()) {
      weaponType = "bow";
    }

    if (armor == null) {
      armor = new ArmorConfig(java.util.List.of(), java.util.List.of(), null, null);
    }

    if (decorations == null) {
      decorations = new DecorationConfig(java.util.List.of(), null, null);
    }

    if (amulets == null) {
      amulets = new AmuletConfig(java.util.List.of(), null, null);
    }

    if (weapons == null) {
      weapons = new WeaponConfig(java.util.List.of(), null, null);
    }

    if (ignoredSkills == null) {
      ignoredSkills = java.util.List.of();
    }

    if (ranking == null) {
      ranking = "free_slots";
    }

    if (equipmentSlotBonus == null) {
      equipmentSlotBonus = 1000L;
    }
  }

  public static GearPoolConfig load(String path) {
    try {
      ObjectMapper mapper = new ObjectMapper();

      return mapper.readValue(Files.readString(Path.of(path)), GearPoolConfig.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load config from " + path, e);
    }
  }

  public void save(String path) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of(path).toFile(), this);
    } catch (IOException e) {
      throw new RuntimeException("Failed to save config to " + path, e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ArmorConfig(
    @JsonProperty("exclude_sets") java.util.List<String> excludeSets,
    @JsonProperty("exclude_skills") java.util.List<String> excludeSkills,
    @JsonProperty("min_rarity") Integer minRarity,
    @JsonProperty("max_rarity") Integer maxRarity
  ) {
    public ArmorConfig {
      if (excludeSets == null) {
        excludeSets = java.util.List.of();
      }
      if (excludeSkills == null) {
        excludeSkills = java.util.List.of();
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DecorationConfig(
    @JsonProperty("exclude_ids") java.util.List<Integer> excludeIds,
    @JsonProperty("min_rarity") Integer minRarity,
    @JsonProperty("max_rarity") Integer maxRarity
  ) {
    public DecorationConfig {
      if (excludeIds == null) {
        excludeIds = java.util.List.of();
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AmuletConfig(
    @JsonProperty("exclude_families") java.util.List<Integer> excludeFamilies,
    @JsonProperty("min_rarity") Integer minRarity,
    @JsonProperty("max_rarity") Integer maxRarity
  ) {
    public AmuletConfig {
      if (excludeFamilies == null) {
        excludeFamilies = java.util.List.of();
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record WeaponConfig(
    @JsonProperty("include_ids") java.util.List<Integer> includeIds,
    @JsonProperty("min_rarity") Integer minRarity,
    @JsonProperty("max_rarity") Integer maxRarity
  ) {
    public WeaponConfig {
      if (includeIds == null) {
        includeIds = java.util.List.of();
      }
    }
  }
}
