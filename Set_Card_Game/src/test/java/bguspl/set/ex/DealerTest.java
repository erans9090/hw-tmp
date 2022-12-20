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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class DealerTest {

    Dealer dealer;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Logger logger;

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, "config.properties"), ui, util);
        Player[] players = {};
        dealer = new Dealer(env, table, players);

    }

    // @AfterEach
    // void tearDown() {
    // }

    @Test
    void updateTimerDisplayTest(){

        withReset();
        withoutReset();
        withWorning();

        // verify(ui).setCountdown(eq(expectedTimer), eq(expectedScore));
    }

    @Test
    void withReset(){

        long expectedTimer = dealer.env.config.turnTimeoutMillis;

        dealer.updateTimerDisplay(true);

        verify(ui).setCountdown(eq(expectedTimer + 999), eq(false));

    }

    // set timer like the game started a second ago
    private void TimerInit(){
        dealer.startTime = System.currentTimeMillis()-1000;

    }
    

    @Test
    void withoutReset(){

        TimerInit();

        long expectedTimer = dealer.env.config.turnTimeoutMillis - 1000;

        dealer.updateTimerDisplay(false);

        verify(ui).setCountdown(eq(expectedTimer + 999), eq(false));

    }

    @Test
    void withWorning(){

        long expectedTimer = dealer.env.config.turnTimeoutMillis;

        dealer.updateTimerDisplay(false);

        verify(ui).setCountdown(eq(expectedTimer + 999), eq(false));

    }


}
