package mhwilds.optimizer.loader;

import static org.assertj.core.api.Assertions.*;

import mhwilds.optimizer.model.ArmorPiece;
import mhwilds.optimizer.model.Weapon;
import org.junit.jupiter.api.Test;

class GameDataLoaderTest {

  @Test
  void loadsAllDataFromRealFiles() {
    GameDataLoader loader = new GameDataLoader("data");
    GameData data = loader.load();

    assertThat(data.skills()).isNotEmpty();
    assertThat(data.armorPieces()).isNotEmpty();
    assertThat(data.decorations()).isNotEmpty();
    assertThat(data.amuletRanks()).isNotEmpty();
    assertThat(data.weapons()).isNotEmpty();

    assertThat(data.skills()).containsKey(-1689391744);
    assertThat(data.skills().get(-1689391744).maxRank()).isEqualTo(5);

    assertThat(data.skills()).containsKey(-315492576);
    assertThat(data.skills().get(-315492576).maxRank()).isEqualTo(3);
  }

  @Test
  void armorPiecesFlattenedFromSets() {
    GameDataLoader loader = new GameDataLoader("data");
    GameData data = loader.load();

    // 194 sets × 5 pieces each (some sets may have fewer)
    assertThat(data.armorPieces().size()).isGreaterThanOrEqualTo(700);
  }

  @Test
  void amuletRanksFlattenedFromFamilies() {
    GameDataLoader loader = new GameDataLoader("data");
    GameData data = loader.load();

    assertThat(data.amuletRanks().size()).isGreaterThanOrEqualTo(180);
  }

  @Test
  void allDecorationSkillIdsExistInSkillMap() {
    GameDataLoader loader = new GameDataLoader("data");
    GameData data = loader.load();

    for (var dec : data.decorations()) {
      for (int skillId : dec.skills().keySet()) {
        assertThat(data.skills())
          .containsKey(skillId)
          .as("Decoration %d references unknown skill %d", dec.gameId(), skillId);
      }
    }
  }

  @Test
  void raritiesLoadedFromRealData() {
    GameDataLoader loader = new GameDataLoader("data");
    GameData data = loader.load();

    assertThat(data.armorPieces()).allMatch(p -> p.rarity() >= 1 && p.rarity() <= 8);
    assertThat(data.decorations()).allMatch(d -> d.rarity() >= 3 && d.rarity() <= 7);
    assertThat(data.amuletRanks()).allMatch(r -> r.rarity() >= 2 && r.rarity() <= 8);
    assertThat(data.weapons()).allMatch(b -> b.rarity() >= 1 && b.rarity() <= 8);
  }

  @Test
  void throwsOnMissingDirectory() {
    assertThatThrownBy(() -> new GameDataLoader("nonexistent").load()).isInstanceOf(
      RuntimeException.class
    );
  }

  @Test
  void armorPiecesHaveSetBonusIdsParsed() {
    GameDataLoader loader = new GameDataLoader("data");
    GameData data = loader.load();

    // Gore set_bonus_id = 722735744 → Gore α pieces should carry it
    ArmorPiece goreHead = data
      .armorPieces()
      .stream()
      .filter(
        p -> p.setName().equals("Gore α") && p.kind() == mhwilds.optimizer.model.ArmorSlot.HEAD
      )
      .findFirst()
      .orElseThrow();
    assertThat(goreHead.setBonusId()).isEqualTo(722735744);
    assertThat(goreHead.setBonusRanks()).containsEntry(2, 1).containsEntry(4, 2);

    // Conga group_bonus_id = 1998066176
    ArmorPiece congaHead = data
      .armorPieces()
      .stream()
      .filter(
        p -> p.setName().equals("Conga α") && p.kind() == mhwilds.optimizer.model.ArmorSlot.HEAD
      )
      .findFirst()
      .orElseThrow();
    assertThat(congaHead.groupBonusId()).isEqualTo(1998066176);
    assertThat(congaHead.groupBonusRanks()).containsEntry(3, 1);

    // Sets without set_bonus have 0 / empty maps
    ArmorPiece orionHead = data
      .armorPieces()
      .stream()
      .filter(
        p -> p.setName().equals("Orion α") && p.kind() == mhwilds.optimizer.model.ArmorSlot.HEAD
      )
      .findFirst()
      .orElseThrow();
    assertThat(orionHead.setBonusId()).isZero();
    assertThat(orionHead.setBonusRanks()).isEmpty();
  }

  @Test
  void setSkillRanksExtractedFromArmorBonuses() {
    GameDataLoader loader = new GameDataLoader("data");
    GameData data = loader.load();

    assertThat(data.setSkillRanks()).isNotEmpty();
    // Gogmapocalypse is the Gogmazios α set bonus: 2 pieces -> L1, 4 pieces -> L2
    assertThat(data.setSkillRanks()).containsKey(5590);
    assertThat(data.setSkillRanks().get(5590)).containsEntry(2, 1).containsEntry(4, 2);
    // Fortifying Pelt (Conga α group bonus): 3 pieces -> L1
    assertThat(data.setSkillRanks()).containsKey(1998066176);
    assertThat(data.setSkillRanks().get(1998066176)).containsEntry(3, 1);
  }

  @Test
  void weaponsLoadedForAllKinds() {
    GameData data = new GameDataLoader("data").load();

    assertThat(data.weapons()).isNotEmpty();
    assertThat(data.weapons()).allMatch(w -> w.kind() != null && !w.kind().isBlank());
    assertThat(
      data
        .weapons()
        .stream()
        .map(w -> w.kind())
        .distinct()
    ).contains("bow", "great-sword", "light-bowgun", "heavy-bowgun");
  }

  @Test
  void kindSpecificFieldsCapturedAsExtras() {
    GameData data = new GameDataLoader("data").load();

    // Great Sword carries sharpness/handicraft; bow carries coatings
    assertThat(
      data
        .weapons()
        .stream()
        .filter(w -> w.kind().equals("great-sword"))
        .findFirst()
    ).isPresent();

    Weapon bow = data
      .weapons()
      .stream()
      .filter(w -> w.kind().equals("bow"))
      .findFirst()
      .orElseThrow();
    assertThat(bow.coatings()).isNotEmpty();
  }
}
