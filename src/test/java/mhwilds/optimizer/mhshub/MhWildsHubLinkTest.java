package mhwilds.optimizer.mhshub;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import mhwilds.optimizer.model.AmuletRank;
import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.ArmorSlot;
import mhwilds.optimizer.model.Build;
import mhwilds.optimizer.model.Decoration;
import mhwilds.optimizer.model.SlotAssignment;
import mhwilds.optimizer.model.SlotTarget;
import mhwilds.optimizer.model.Weapon;
import org.junit.jupiter.api.Test;

class MhWildsHubLinkTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final ArmorPiece HOPE_HEAD = armor(ArmorSlot.HEAD);
  private static final ArmorPiece HOPE_CHEST = armor(ArmorSlot.CHEST);
  private static final ArmorPiece HOPE_ARMS = armor(ArmorSlot.ARMS);
  private static final ArmorPiece HOPE_WAIST = armor(ArmorSlot.WAIST);
  private static final ArmorPiece HOPE_LEGS = armor(ArmorSlot.LEGS);

  private static final Decoration VENOM = new Decoration(
    -2144349312,
    "Venom Jewel [1]",
    1,
    Map.of(),
    SlotTarget.ARMOR
  );

  @Test
  void shareUrlDecodesToExpectedPayloadForHopeSet() {
    Weapon hope = new Weapon(
      1,
      "long-sword",
      "Hope Edge I",
      260,
      0,
      new int[] { 2, 2, 2 },
      Map.of(),
      List.of()
    );

    AmuletRank powerCharm = new AmuletRank(-922609472, 1, "Power Charm I", Map.of());

    Build build = new Build(
      new ArmorPiece[] { HOPE_HEAD, HOPE_CHEST, HOPE_ARMS, HOPE_WAIST, HOPE_LEGS },
      List.of(
        new SlotAssignment(VENOM, ArmorSlot.HEAD, 0),
        new SlotAssignment(VENOM, ArmorSlot.CHEST, 1)
      ),
      List.of(new SlotAssignment(VENOM, null, 0)),
      powerCharm,
      hope
    );

    JsonNode payload = decode(MhWildsHubLink.shareUrl(build));

    assertThat(payload.get("w").toString()).isEqualTo("[3,\"Tachi_000\"]");
    assertThat(payload.get("wd").toString()).isEqualTo("[\"ACC_ID_0030\",null,null]");
    assertThat(payload.get("am").asText()).isEqualTo("23");
    assertThat(payload.get("amd").toString()).isEqualTo("[]");
    assertThat(payload.get("a").toString()).isEqualTo("[0,1,2,3,4]");
    assertThat(payload.get("ad").toString()).isEqualTo(
      "[[\"ACC_ID_0030\",null,null],[null,\"ACC_ID_0030\",null],[null,null,null]," +
        "[null,null,null],[null,null,null]]"
    );
  }

  @Test
  void shareUrlHandlesOmittedEquipment() {
    Build build = new Build(
      new ArmorPiece[] { null, HOPE_CHEST, null, null, null },
      List.of(),
      List.of(),
      null,
      null
    );

    JsonNode payload = decode(MhWildsHubLink.shareUrl(build));

    assertThat(payload.has("w")).isFalse();
    assertThat(payload.get("wd").toString()).isEqualTo("[]");
    assertThat(payload.get("a").toString()).isEqualTo("[null,1,null,null,null]");
    assertThat(payload.get("ad").toString()).isEqualTo("[[],[null,null,null],[],[],[]]");
    assertThat(payload.get("amd").toString()).isEqualTo("[]");
    assertThat(payload.has("am")).isFalse();
  }

  @Test
  void shareUrlRejectsUnknownDeco() {
    Build build = new Build(
      new ArmorPiece[] { HOPE_HEAD, HOPE_CHEST, HOPE_ARMS, HOPE_WAIST, HOPE_LEGS },
      List.of(
        new SlotAssignment(
          new Decoration(999, "Mystery", 1, Map.of(), SlotTarget.ARMOR),
          ArmorSlot.HEAD,
          0
        )
      ),
      List.of(),
      null,
      null
    );

    assertThatThrownBy(() -> MhWildsHubLink.shareUrl(build))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("decoration 999");
  }

  private static JsonNode decode(String url) {
    assertThat(url).startsWith("https://www.mhwildshub.com/builder?data=");

    String data = url.substring(url.indexOf("?data=") + "?data=".length());
    String base64 = URLDecoder.decode(data, StandardCharsets.UTF_8);
    String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);

    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      throw new AssertionError("payload is not valid JSON: " + json, e);
    }
  }

  private static ArmorPiece armor(ArmorSlot kind) {
    return new ArmorPiece(2, "Hope", kind, new int[] { 2, 2, 2 }, Map.of(), 6, 220);
  }
}
