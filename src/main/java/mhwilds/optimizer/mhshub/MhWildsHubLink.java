package mhwilds.optimizer.mhshub;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.AppraisedTalisman;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.SlotAssignment;

public final class MhWildsHubLink {

  private static final String BASE_URL = "https://www.mhwildshub.com";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final TypeReference<Map<String, Map<String, List<String>>>> WEAPON_REF =
    new TypeReference<>() {};

  private static final TypeReference<Map<String, Map<String, Integer>>> ARMOR_REF =
    new TypeReference<>() {};

  private static final TypeReference<Map<String, String>> DECO_REF = new TypeReference<>() {};

  private static final TypeReference<Map<String, Map<String, String>>> AMULET_REF =
    new TypeReference<>() {};

  private static volatile Mappings mappings;

  private MhWildsHubLink() {}

  private record Mappings(
    Map<String, Map<String, List<String>>> weapons,
    Map<String, Map<String, Integer>> armor,
    Map<String, String> decos,
    Map<String, Map<String, String>> amulets
  ) {}

  public static String shareUrl(Build build) {
    if (build == null) {
      throw new NullPointerException("build must not be null");
    }

    String payload = payloadFor(build);

    String base64 = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

    return BASE_URL + "/builder?data=" + encodeURIComponent(base64);
  }

  static Mappings mappings() {
    Mappings local = mappings;

    if (local == null) {
      synchronized (MhWildsHubLink.class) {
        local = mappings;

        if (local == null) {
          mappings = local = loadMappings();
        }
      }
    }

    return local;
  }

  private static Mappings loadMappings() {
    try (InputStream in = MhWildsHubLink.class.getResourceAsStream("/mhshub_ids.json")) {
      if (in == null) {
        throw new IllegalStateException("mhshub_ids.json mapping resource is missing");
      }

      Map<String, Object> root = MAPPER.readValue(in, new TypeReference<Map<String, Object>>() {});

      Mappings m = new Mappings(
        MAPPER.convertValue(root.get("weapons"), WEAPON_REF),
        MAPPER.convertValue(root.get("armor"), ARMOR_REF),
        MAPPER.convertValue(root.get("decos"), DECO_REF),
        MAPPER.convertValue(root.get("amulets"), AMULET_REF)
      );

      sinkMappings(m);

      return m;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load mhshub_ids.json", e);
    }
  }

  private static void sinkMappings(Mappings m) {
    for (var bySlot : m.armor().values()) {
      for (int id : bySlot.values()) {
        checkRange(id, "armor");
      }
    }
  }

  private static void checkRange(int id, String kind) {
    if (id < 0) {
      throw new IllegalStateException("invalid " + kind + " id: " + id);
    }
  }

  private static String payloadFor(Build build) {
    Mappings m = mappings();
    LinkedHashMap<String, Object> c = new LinkedHashMap<>();

    if (build.weapon() != null) {
      c.put("w", weaponTuple(build, m));
      c.put("wd", weaponDecos(build, m));
    } else {
      c.put("wd", new String[0]);
    }

    if (build.amuletRank() != null) {
      c.put("am", amuletId(build, m));
    }

    c.put("amd", new String[0]);

    List<Integer> armorIds = new ArrayList<>();
    List<String[]> armorDecos = new ArrayList<>();

    for (ArmorSlot slot : ArmorSlot.values()) {
      var piece = build.armorPiece(slot);

      if (piece == null) {
        armorIds.add(null);
        armorDecos.add(new String[0]);
        continue;
      }

      armorIds.add(armorId(piece.gameId(), piece.kind(), m));
      armorDecos.add(pieceDecos(build, piece.kind(), piece.slotCount(), m));
    }

    c.put("a", armorIds);
    c.put("ad", armorDecos);

    try {
      return MAPPER.writeValueAsString(c);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to serialize hub payload", e);
    }
  }

  private static List<Object> weaponTuple(Build build, Mappings m) {
    String kind = build.weapon().kind();
    String gameId = String.valueOf(build.weapon().gameId());
    Map<String, List<String>> byId = m.weapons().get(kind);

    if (byId == null) {
      throw new IllegalStateException("no hub ids for weapon kind " + kind);
    }

    List<String> hub = byId.get(gameId);

    if (hub == null) {
      throw new IllegalStateException("no hub id for weapon " + kind + ":" + gameId);
    }

    return List.of(Integer.parseInt(hub.get(0)), hub.get(1));
  }

  private static String[] weaponDecos(Build build, Mappings m) {
    String[] decos = new String[build.weapon().slotCount()];

    for (SlotAssignment sa : build.weaponDecorations()) {
      decos[sa.slotIndexWithinPiece()] = decoId(sa, m);
    }

    return decos;
  }

  private static int armorId(int gameId, ArmorSlot kind, Mappings m) {
    return require(m.armor(), String.valueOf(gameId), kind.name().toLowerCase(), "armor");
  }

  private static String[] pieceDecos(Build build, ArmorSlot target, int slotCount, Mappings m) {
    String[] decos = new String[slotCount];

    for (SlotAssignment sa : build.armorDecorations()) {
      if (sa.targetPiece() == target) {
        decos[sa.slotIndexWithinPiece()] = decoId(sa, m);
      }
    }

    return decos;
  }

  private static String amuletId(Build build, Mappings m) {
    String family = String.valueOf(build.amuletRank().familyGameId());
    String rank = String.valueOf(build.amuletRank().rankLevel());

    if (AppraisedTalisman.isAppraisedFamilyId(build.amuletRank().familyGameId())) {
      throw new IllegalStateException(
        "appraised talisman '" +
          build.amuletRank().name() +
          "' has no MH Wilds Hub entry and cannot be shared"
      );
    }

    return require(m.amulets(), family, rank, "amulet");
  }

  private static String decoId(SlotAssignment sa, Mappings m) {
    String id = m.decos().get(String.valueOf(sa.decoration().gameId()));

    if (id == null) {
      throw new IllegalStateException(
        "no hub id for decoration " + sa.decoration().gameId() + " (" + sa.decoration().name() + ")"
      );
    }

    return id;
  }

  private static <T> T require(
    Map<String, Map<String, T>> table,
    String outer,
    String inner,
    String what
  ) {
    Map<String, T> innerMap = table.get(outer);

    if (innerMap == null) {
      throw new IllegalStateException("no hub ids for " + what + " key " + outer);
    }

    T id = innerMap.get(inner);

    if (id == null) {
      throw new IllegalStateException("no hub id for " + what + " " + outer + ":" + inner);
    }

    return id;
  }

  private static String encodeURIComponent(String s) {
    StringBuilder sb = new StringBuilder(s.length());

    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);

      if (isUnreserved(ch)) {
        sb.append(ch);
      } else {
        for (byte b : String.valueOf(ch).getBytes(StandardCharsets.UTF_8)) {
          sb.append(String.format("%%%02X", b));
        }
      }
    }

    return sb.toString();
  }

  private static boolean isUnreserved(char ch) {
    return (
      (ch >= 'A' && ch <= 'Z') ||
      (ch >= 'a' && ch <= 'z') ||
      (ch >= '0' && ch <= '9') ||
      ch == '-' ||
      ch == '_' ||
      ch == '.' ||
      ch == '~' ||
      ch == '!' ||
      ch == '*' ||
      ch == '\'' ||
      ch == '(' ||
      ch == ')'
    );
  }
}
