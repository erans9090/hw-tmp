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

import java.util.logging.Logger;

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
    void paneltyTest() {

        // calculate the expected freeze time
        long expectedFrozenTimer = player.env.config.penaltyFreezeMillis;

        // call the method we are testing
        player.panelty();

        // check that the freeze time was increased correctly
        assertEquals(expectedFrozenTimer, player.frozenTimer);
    }

    @Test
    void emptyHashSetTest(){
        
        // insert dummy vlues into the player's hash set and the incoming actions
        Integer[] dummy = {0,5};
        player.setToCheck.add(dummy);
        player.incomingActions.add(dummy);

        //test that they are not empty:
        assertEquals(false, player.setToCheck.isEmpty() | player.incomingActions.isEmpty());

        // call the method we are testing
        player.emptyHashSet();

        // check that the freeze time was increased correctly
        assertEquals(true, player.setToCheck.isEmpty() & player.incomingActions.isEmpty());

    }
}