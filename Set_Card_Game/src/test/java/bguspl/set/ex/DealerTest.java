package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class DealerTest {

    Dealer dealer;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;

    private Integer[] slotToCard;
    private Integer[] cardToSlot;
    private Table table;
    @Mock
    private Logger logger;

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        
        Env env = new Env(logger, new Config(logger, "config.properties"), ui, util);
        slotToCard = new Integer[env.config.tableSize];
        cardToSlot = new Integer[env.config.deckSize];
        table = new Table(env, slotToCard, cardToSlot);
        Player[] players = {};
        dealer = new Dealer(env, table, players);

        dealer.placeCardsOnTable();




    }

    // --- updateTimerDisplay test --- //

    @Test
    void withReset(){

        long expectedTimer = dealer.env.config.turnTimeoutMillis;

        dealer.updateTimerDisplay(true);

//      +999 so the display would show the current timer for a whole second
//      otherwise after 1 milis the timer could be upadated
        verify(ui).setCountdown(eq(expectedTimer + 999), eq(false));

    }

    // set timer like the game started a second ago
    private void TimerInit(long time){
        dealer.startTime = time;

    }
    

    @Test
    void withoutReset(){

        TimerInit(System.currentTimeMillis()-1000);

        long expectedTimer = dealer.env.config.turnTimeoutMillis - 1000;

        dealer.updateTimerDisplay(false);

//      +999 so the display would show the current timer for a whole second
//      otherwise after 1 milis the timer could be upadated
        verify(ui).setCountdown(eq(expectedTimer + 999), eq(false));

    }

    @Test
    void withWorning(){

        // initial the time like it started before dealer.env.config.turnTimeoutMillis + dealer.env.config.turnTimeoutWarningMillis - 1000milis
        // meaning we are at the warning time

        TimerInit(System.currentTimeMillis() - dealer.env.config.turnTimeoutMillis + dealer.env.config.turnTimeoutWarningMillis - 1000);

        long expectedTimer = dealer.env.config.turnTimeoutWarningMillis - 1000;

        dealer.updateTimerDisplay(false);

//      +10 becouse in the warning time the clock updates every 10 millis
        verify(ui).setCountdown(eq(expectedTimer + 10), eq(true));

    }
    

    // --- removeAllCardsFromTable test --- //

    @Test
    void removeAllCardsFromTableTest(){

        
        dealer.removeAllCardsFromTable();
        
        // make sure the table is empty
        for(int i = 0; i < table.slotToCard.length ; i ++){
            assertEquals(null, table.slotToCard[i]);
        }
        for(int i = 0; i < table.cardToSlot.length ; i ++){
            assertEquals(null, table.cardToSlot[i]);
        }

        // thevexpected deck is a full deck of cards - a sorted list from 0 to 80
        List<Integer> expectedDeck = IntStream.range(0, dealer.env.config.deckSize).boxed().collect(Collectors.toList());
        
        // cards can return to the deck in a different order therefore a sort need to be done
        // to check if the expected and the real has the same cards
        Collections.sort(dealer.deck);
        // make sure all cards returned to the deck
        assertEquals(expectedDeck, dealer.deck);


        // make sure ui removed all cards from the table
        for (int i = 0; i < dealer.env.config.tableSize; i++) {
            verify(ui).removeCard(eq(i));
        }

    }
    

}
