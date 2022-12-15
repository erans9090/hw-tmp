package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Queue;
import java.util.logging.Logger;

// import org.junit.Assert;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.CsvSource;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlayerTest {

    Player player;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;

    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.getScore() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, "config.properties"), ui, util);
        player = new Player(env, dealer, table, 0, false);
        // table.slotToCard[1] = 3;
        assertInvariants();

    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    @Test
    void point() {

        // force table.countCards to return 3
        // when(player.getScore()).thenReturn(3); // this part is just for demonstration

        // calculate the expected score for later
        int expectedScore = player.getScore() + 1;

        // call the method we are testing
        player.point();

        // check that the score was increased correctly
        assertEquals(expectedScore, player.getScore());

        // check that ui.setScore was called with the player's id and the correct score
        verify(ui).setScore(eq(player.id), eq(expectedScore));
    }

    @Test
    void panelty() {

        // calculate the expected freeze time
        long expectedFrozenTimer = player.env.config.penaltyFreezeMillis;

        // call the method we are testing
        player.panelty();

        // check that the freeze time was increased correctly
        assertEquals(expectedFrozenTimer, player.frozenTimer);
    }

    @Test
    void keyPressed(int slot){
        
        // force table.slotToCard[1] to return 3
        // when(slot).thenReturn(1);
        // when(table.slotToCard[1]).thenReturn(3);

        // slot added to incoming actions only if its legal to add it

        slot = 1;

        Queue<Integer[]> expectedincomingActions = player.incomingActions;
        Integer[] tmp = {table.slotToCard[slot],slot};
        expectedincomingActions.add(tmp);

        // call the method we are testing
        player.keyPressed(slot);

        // check that the frozenTimer was decreased correctly
        assertEquals(expectedincomingActions, player.incomingActions);

        // check that ui.setScore was called with the player's id and the correct score
        // verify(ui).setFreeze(eq(player.id), eq(expectedincomingActions));
    }
}