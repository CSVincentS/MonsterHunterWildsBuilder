package mhwilds.optimizer.ranking;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;
import mhwilds.optimizer.model.*;
import org.junit.jupiter.api.Test;

class FreeSlotRankingTest {

  private ArmorPiece piece(ArmorSlot kind, int[] slots) {
    return new ArmorPiece(0, "Set", kind, slots, Map.of(), 0, 0);
  }

  private ArmorPiece defensePiece(ArmorSlot kind, int[] slots, int maxDefense) {
    return new ArmorPiece(0, "Set", kind, slots, Map.of(), 0, maxDefense);
  }

  private List<Build> sorted(Build a, Build b) {
    return Stream.of(a, b).sorted(new FreeSlotRanking()).toList();
  }

  @Test
  void higherFreeSlotsRankedFirst() {
    ArmorPiece[] piecesA = {
      piece(ArmorSlot.HEAD, new int[] { 3, 3 }),
      piece(ArmorSlot.CHEST, new int[] {}),
      piece(ArmorSlot.ARMS, new int[] {}),
      piece(ArmorSlot.WAIST, new int[] {}),
      piece(ArmorSlot.LEGS, new int[] {}),
    };
    Build buildA = new Build(
      piecesA,
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    ArmorPiece[] piecesB = {
      piece(ArmorSlot.HEAD, new int[] { 1, 1, 1 }),
      piece(ArmorSlot.CHEST, new int[] {}),
      piece(ArmorSlot.ARMS, new int[] {}),
      piece(ArmorSlot.WAIST, new int[] {}),
      piece(ArmorSlot.LEGS, new int[] {}),
    };
    Build buildB = new Build(
      piecesB,
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    FreeSlotRanking ranking = new FreeSlotRanking();
    List<Build> builds = List.of(buildB, buildA);
    List<Build> sorted = builds.stream().sorted(ranking).toList();

    assertThat(sorted.get(0)).isEqualTo(buildA);
  }

  @Test
  void equalFreeSlotScoresRankedByHigherMaxDefenseFirst() {
    ArmorPiece[] lowDef = {
      defensePiece(ArmorSlot.HEAD, new int[] { 3, 3 }, 50),
      piece(ArmorSlot.CHEST, new int[] {}),
      piece(ArmorSlot.ARMS, new int[] {}),
      piece(ArmorSlot.WAIST, new int[] {}),
      piece(ArmorSlot.LEGS, new int[] {}),
    };
    ArmorPiece[] highDef = {
      defensePiece(ArmorSlot.HEAD, new int[] { 3, 3 }, 120),
      piece(ArmorSlot.CHEST, new int[] {}),
      piece(ArmorSlot.ARMS, new int[] {}),
      piece(ArmorSlot.WAIST, new int[] {}),
      piece(ArmorSlot.LEGS, new int[] {}),
    };
    Build buildLow = new Build(
      lowDef,
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );
    Build buildHigh = new Build(
      highDef,
      List.of(),
      List.of(),
      null,
      new Weapon(1, "bow", "Bow", 100, 0, new int[] {}, Map.of(), List.of())
    );

    assertThat(buildLow.freeSlotScore()).isEqualTo(buildHigh.freeSlotScore());
    List<Build> out = sorted(buildLow, buildHigh);
    assertThat(out.get(0)).isEqualTo(buildHigh);
  }

  @Test
  void factoryCreatesFreeSlotRanking() {
    RankingStrategy strategy = RankingFactory.create("free_slots");

    assertThat(strategy).isInstanceOf(FreeSlotRanking.class);
  }

  @Test
  void factoryThrowsOnUnknown() {
    assertThatThrownBy(() -> RankingFactory.create("nonexistent")).isInstanceOf(
      IllegalArgumentException.class
    );
  }
}
