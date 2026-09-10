package mhwilds.optimizer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-weapon-kind ignored-skill seeds loaded from {@code weapon-ignored-skills.json}. */
public final class WeaponIgnoredSkillSeeds {

  private static final String RESOURCE = "/weapon-ignored-skills.json";

  private final Map<String, Set<Integer>> seeds;

  private WeaponIgnoredSkillSeeds(Map<String, Set<Integer>> seeds) {
    this.seeds = seeds;
  }

  public static WeaponIgnoredSkillSeeds defaultOnClasspath() {
    ObjectMapper mapper = new ObjectMapper();

    try (InputStream in = WeaponIgnoredSkillSeeds.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing classpath resource " + RESOURCE);
      }

      Map<String, List<Integer>> raw = mapper.readValue(
        in,
        new com.fasterxml.jackson.core.type.TypeReference<Map<String, List<Integer>>>() {}
      );

      Map<String, Set<Integer>> parsed = new HashMap<>();
      raw.forEach((kind, ids) -> parsed.put(kind, Set.copyOf(ids)));

      return new WeaponIgnoredSkillSeeds(Map.copyOf(parsed));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load " + RESOURCE, e);
    }
  }

  /** Ignored-skill ids for a weapon kind; empty set for unknown kinds. */
  public Set<Integer> seedFor(String kind) {
    return seeds.getOrDefault(kind, Set.of());
  }

  public Map<String, Set<Integer>> all() {
    return seeds;
  }
}
