package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


enum GameMode{
    NO_TIMER,
    LAST_ACTION_TIMER,
    REGULAR
}

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;
    

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time remain till the dealer needs to reshuffle the deck due to turn timeout.
     */
    // private long reshuffleTime = Long.MAX_VALUE;


    /**
     * The time of the loop between dealer needs to reshuffle the deck.
     */
    // private long loopTime;

    /**
     * should the warn on the timer be turned on
     */
    private boolean warn;

    /**
     * count the time from last action 
     */
    private int Timecounter;

    /**
     * the timer interval time secion
     */
    private int timerIntervalMills = 1000;

    private GameMode mode;

    protected long startTime;



    /**
     * Array of the players threads
     */
    private Thread[] threads;

    public Dealer(Env env, Table table, Player[] players) {

        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        // implemnt:

        // set game mode
        // if( env.config.turnTimeoutMillis > 0)
        //     mode = GameMode.REGULAR;
        // else if(env.config.turnTimeoutMillis == 0)
        //     mode = GameMode.LAST_ACTION_TIMER;
        // else
        //     mode = GameMode.NO_TIMER;


        // if(mode == GameMode.REGULAR)
        //     loopTime = env.config.turnTimeoutMillis;
        // else
        // loopTime = reshuffleTime;
        terminate = false;
        warn = false;


    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        
        // info sys out
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());

        // start players threads
        threads = new Thread[players.length];
        for(int i = 0; i < players.length;i++){
            threads[i] = new Thread(players[i]);
            threads[i].start();
            try{
                synchronized(table.lock){
                    table.lock.wait();}}
            catch(InterruptedException ex){}
        }


        //  ----MAIN LOOP----  //
        while (!shouldFinish()) {

            // warn = reshuffleTime < env.config.turnTimeoutWarningMillis;
            
            placeCardsOnTable();
            // if(mode != GameMode.NO_TIMER && loopTime != Long.MAX_VALUE)
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }

        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }


    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() - startTime <=  + env.config.turnTimeoutMillis ) {

            // setInterval();
            sleepUntilWokenOrTimeout();
            // if(mode != GameMode.NO_TIMER)
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
        // removeAllTokens();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        //terminate other thredes:
        for(int i = players.length - 1; i >= 0;i--){
            players[i].terminate();
            try { players[i].playerThread.join(); } catch (InterruptedException ignored) {}
        }
        terminate = true;
    }

    /**
     * Called when the game finnished and should terminated.
     */
    public void terminateGameFinnished() {
        env.ui.setCountdown(0, false);
        announceWinners();

        terminate();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        if(env.util.findSets(deck, 1).size() == 0)
            terminateGameFinnished();
        return terminate;
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable() {
        // CRITICAL SECTION dealer should not be interupted
        //  until a set is found or the queue is empty
        try{
            table.lockDealerQueue.acquire();
            while(!table.setsToCheckQueue.isEmpty()){
                int pId = table.setsToCheckQueue.poll();
                // System.out.println("check player " + pId + " and now the queue is: " + table.setsToCheckQueue);

                synchronized(players[pId].actionsLocker){
                    int[][] playerSet = players[pId].getSetFromQueue();
                    int[] playerCards = new int[3];
                    int[] playerSlots = new int[3];
                    // unpack set to cards array and slots array
                    for(int i = 0; i<3; i++) {
                        playerCards[i] = playerSet[i][0];
                        playerSlots[i] = playerSet[i][1];
                    }
                    
                    //check if the set is still relevant
                    boolean valid = true;
                    for(int i = 0 ; i < playerSet.length && valid; i++){
                        if(table.cardToSlot[playerCards[i]] == null || !table.cardToSlot[playerCards[i]].equals(playerSlots[i])){
                            table.removeTokensOfPlayer(pId, playerSlots);
                            System.out.println("board has changed!");
                            valid = false;
                        }
                    }
                    
                    if(valid){
                        // System.out.println("Dealer checks " + pId + " set: " + Arrays.toString(playerCards));
                        
                        //got a point:
                        if(env.util.testSet(playerCards)){
                            for(int i = 0 ; i< playerSet.length;i++){
                                table.removeToken(pId,table.cardToSlot[playerCards[i]]);
                                table.removeCard(table.cardToSlot[playerCards[i]]);
                            }
                            players[pId].emptyQueue();
                            players[pId].point();

                            //reset timer:
                            // mode check
                            updateTimerDisplay(true);

                            
                            // System.out.println("player " + pId + " got a point and now the queue is: " + table.setsToCheckQueue.toString());
                        
                        } //panelty:
                        else{
                            // if(!players[pId].human){
                            //     for(int i = 0 ; i< playerSet.length;i++)
                            //         table.removeToken(pId,table.cardToSlot[playerCards[i]]);
                            //     players[pId].emptyQueue();
                            // }
                            players[pId].panelty();
                            // System.out.println("player " + pId + " got a panelty and now the queue is: " + table.setsToCheckQueue.toString());

                        }
                    }
                }  
            }
        } catch(InterruptedException ex) {System.out.println("----didn't catch dealer queue-----");}
        table.lockDealerQueue.release();
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        //implement

        //shuffel 12 cards from deck to the board randomly and place them randomly on the table
        Integer [] arr = new Integer[(int)(env.config.rows*env.config.columns)];
        for(int i = 0; i < env.config.rows*env.config.columns; i++)
            arr[i] = i;
        List<Integer> lst = Arrays.asList(arr);
        Collections.shuffle(lst);


        for (int slot :lst){
            if (table.slotToCard[slot] == null){
                if(deck.size() > 0){
                    Random r = new Random();
                    int cardIndex = r.nextInt(deck.size());
                    int cardId = deck.get(cardIndex);
                    deck.remove(cardIndex);
                    table.placeCard(cardId, slot);
                }
            }
        }
    }


    private void setInterval(){
        
        long current = System.currentTimeMillis();
        long partial = (current - startTime) % timerIntervalMills;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {

        // implement
        // update players freeze timer

        if(warn)    // by default 1000 miliseconds / 10 miliseconds if warn
            timerIntervalMills = 10;
        else
            timerIntervalMills = 1000;
        
        long current = System.currentTimeMillis();
        long partial = timerIntervalMills - (current - startTime) % timerIntervalMills;

        // System.out.println("Before partial: - " + partial);

        try{Thread.sleep(partial);} 
        catch(InterruptedException ex){}

        // System.out.println("After partial: - " + (timerIntervalMills - (current - startTime)% timerIntervalMills));



        // return 
 

        // if(mode == GameMode.NO_TIMER){
        //     return;
        // }

        // if(mode == GameMode.LAST_ACTION_TIMER)
        //     Timecounter += timerIntervalMills;
        

        // if(reshuffleTime <= env.config.turnTimeoutWarningMillis)
        //     warn = true;
        
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // implement

        long current = System.currentTimeMillis();

        if(reset){
            startTime = current;
        }        

        warn = current > startTime + env.config.turnTimeoutMillis - env.config.turnTimeoutWarningMillis ;
        
        
        // if(warn)
        //     timerIntervalMills = 10;

        long partial = current - startTime;

        if(!warn)
            env.ui.setCountdown(env.config.turnTimeoutMillis - partial + 999, warn);
        else
            env.ui.setCountdown(env.config.turnTimeoutMillis - partial + 10, warn);

        
        
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // implement

        if(terminate)
            return;

        if(env.config.turnTimeoutMillis < 0)
            if(env.util.findSets(deck, 1).size() != 0)
                return;

        System.out.println("reshuffle!");

        Integer [] arr = new Integer[(int)(env.config.rows*env.config.columns)];
        for(int i = 0; i < env.config.rows*env.config.columns; i++)
            arr[i] = i;
        
        List<Integer> lst = Arrays.asList(arr);
        Collections.shuffle(lst);

        for (int slot :lst){
            if (table.slotToCard[slot] != null){
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }
        }

        try{
            table.lockDealerQueue.acquire();
            table.setsToCheckQueue.clear();
            for (int pId = 0 ; pId< players.length ; pId++){
                synchronized(players[pId].actionsLocker){
                        players[pId].incomingActions.clear();
                    }
                }
        } catch(InterruptedException ex) {System.out.println("----didn't catch dealer queue-----");}

        table.lockDealerQueue.release();
        table.removeAllTokens();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // implement

        int maxScore = -1;
        int countWinners = 0;
        for(Player p : players){
            if(maxScore == p.getScore()){
                countWinners += 1;
            }
            if(maxScore < p.getScore()){
                maxScore = p.getScore();
                countWinners = 1;
            }
        }

        int[] winners = new int[countWinners];
        int index = 0;
        for(Player p : players){
            if(p.getScore() == maxScore){
                winners[index] = p.id;
                index += 1;
            }
        }

        env.ui.announceWinner(winners);
    }

}



