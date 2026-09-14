package mhwilds.optimizer.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GearPoolConfig(
  @JsonProperty("required_skills") Map<String, Integer> requiredSkills,
  @JsonProperty("weapon_type") String weaponType,
  @JsonProperty("armor") ArmorConfig armor,
  @JsonProperty("decorations") DecorationConfig decorations,
  @JsonProperty("amulets") AmuletConfig amulets,
  @JsonProperty("weapons") WeaponConfig weapons,
  @JsonProperty("ignored_skills") List<Integer> ignoredSkills,
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
      armor = new ArmorConfig(List.of(), List.of(), null, null);
    }

    if (decorations == null) {
      decorations = new DecorationConfig(List.of(), null, null);
    }

    if (amulets == null) {
      amulets = new AmuletConfig(List.of(), null, null);
    }

    if (weapons == null) {
      weapons = new WeaponConfig(List.of(), null, null);
    }

    if (ignoredSkills == null) {
      ignoredSkills = List.of();
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

  public GearPoolConfig withRequiredSkills(Map<String, Integer> requiredSkills) {
    return new GearPoolConfig(
      requiredSkills,
      weaponType,
      armor,
      decorations,
      amulets,
      weapons,
      ignoredSkills,
      ranking,
      equipmentSlotBonus
    );
  }

  public GearPoolConfig withWeaponType(String weaponType) {
    return new GearPoolConfig(
      requiredSkills,
      weaponType,
      armor,
      decorations,
      amulets,
      weapons,
      ignoredSkills,
      ranking,
      equipmentSlotBonus
    );
  }

  public GearPoolConfig withArmor(ArmorConfig armor) {
    return new GearPoolConfig(
      requiredSkills,
      weaponType,
      armor,
      decorations,
      amulets,
      weapons,
      ignoredSkills,
      ranking,
      equipmentSlotBonus
    );
  }

  public GearPoolConfig withDecorations(DecorationConfig decorations) {
    return new GearPoolConfig(
      requiredSkills,
      weaponType,
      armor,
      decorations,
      amulets,
      weapons,
      ignoredSkills,
      ranking,
      equipmentSlotBonus
    );
  }

  public GearPoolConfig withAmulets(AmuletConfig amulets) {
    return new GearPoolConfig(
      requiredSkills,
      weaponType,
      armor,
      decorations,
      amulets,
      weapons,
      ignoredSkills,
      ranking,
      equipmentSlotBonus
    );
  }

  public GearPoolConfig withWeapons(WeaponConfig weapons) {
    return new GearPoolConfig(
      requiredSkills,
      weaponType,
      armor,
      decorations,
      amulets,
      weapons,
      ignoredSkills,
      ranking,
      equipmentSlotBonus
    );
  }

  public GearPoolConfig withRanking(String ranking) {
    return new GearPoolConfig(
      requiredSkills,
      weaponType,
      armor,
      decorations,
      amulets,
      weapons,
      ignoredSkills,
      ranking,
      equipmentSlotBonus
    );
  }

  public GearPoolConfig withEquipmentSlotBonus(Long equipmentSlotBonus) {
    return new GearPoolConfig(
      requiredSkills,
      weaponType,
      armor,
      decorations,
      amulets,
      weapons,
      ignoredSkills,
      ranking,
      equipmentSlotBonus
    );
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ArmorConfig(
    @JsonProperty("exclude_sets") List<String> excludeSets,
    @JsonProperty("exclude_skills") List<String> excludeSkills,
    @JsonProperty("min_rarity") Integer minRarity,
    @JsonProperty("max_rarity") Integer maxRarity
  ) {
    public ArmorConfig {
      if (excludeSets == null) {
        excludeSets = List.of();
      }
      if (excludeSkills == null) {
        excludeSkills = List.of();
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DecorationConfig(
    @JsonProperty("exclude_ids") List<Integer> excludeIds,
    @JsonProperty("min_rarity") Integer minRarity,
    @JsonProperty("max_rarity") Integer maxRarity
  ) {
    public DecorationConfig {
      if (excludeIds == null) {
        excludeIds = List.of();
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AmuletConfig(
    @JsonProperty("exclude_families") List<Integer> excludeFamilies,
    @JsonProperty("min_rarity") Integer minRarity,
    @JsonProperty("max_rarity") Integer maxRarity
  ) {
    public AmuletConfig {
      if (excludeFamilies == null) {
        excludeFamilies = List.of();
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record WeaponConfig(
    @JsonProperty("include_ids") List<Integer> includeIds,
    @JsonProperty("min_rarity") Integer minRarity,
    @JsonProperty("max_rarity") Integer maxRarity
  ) {
    public WeaponConfig {
      if (includeIds == null) {
        includeIds = List.of();
      }
    }
  }
}
