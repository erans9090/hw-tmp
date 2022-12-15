package bguspl.set.ex;

import bguspl.set.Env;

import java.nio.channels.Pipe;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
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



    private Thread mThread;



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

        //intilaize the dealer thread:
        mThread = Thread.currentThread();
        

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

        for(int i = players.length - 1; i >= 0;i--){
            System.out.println("# try to close player " + i);
            players[i].terminate();
            // boolean Closed = false;
            players[i].playerThread.interrupt();
            while(players[i].playerThread.isAlive()){
                System.out.println("# player " + i + " is -> " +players[i].playerThread.isAlive() + " trying to close it loop");
                try { 
                    players[i].playerThread.join();
                    // Closed = true; 
                } 
                catch (InterruptedException ignored) {
                    System.out.println("# player "+ i +" close interupted");
                }
            }
            System.out.println("# player " + i + " spose to be close ---------------------");
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

        System.out.println("closing --------------------------------");
        terminate = true;
        mThread.interrupt();
        
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


        // boolean catchDealerQueue = false;
        // while(!catchDealerQueue){
            // System.out.println("dealer trying to acquire..");
            try{
                if(terminate)
                    return;

                table.lockDealerQueue.acquire();
                // System.out.println("dealer succeed to acquire!");

                // catchDealerQueue = true;

                // iterate dealerws queue:

                while(!table.setsToCheckQueue.isEmpty()){

                    int pId = table.setsToCheckQueue.poll();
                    System.out.println("check player " + pId + " and now the queue is: " + table.setsToCheckQueue);

                    synchronized(players[pId].actionsLocker){

                        //if set to check size smaller then 3 empty it:
                        //happens when other player got a point and the card removed
                        if(players[pId].setToCheck.size() < 3){
                            players[pId].emptyHashSet();
                            players[pId].waitingToCheck = false;
                            threads[pId].interrupt();
                            players[pId].wakeAi();
                            continue;
                        }

                        // get the player's set (cards and slots):
                        int[][] playerSet = players[pId].getSetFromHahSet();
                        int[] playerCards = new int[3];
                        int[] playerSlots = new int[3];

                        for(int i = 0; i < playerSet.length; i++) {
                            playerCards[i] = playerSet[i][0];
                            playerSlots[i] = playerSet[i][1];
                        }

                        // CHECK SET :

                        // (1) check if the set is still relevant
                        boolean valid = true;
                        for(int i = 0 ; i < playerSet.length && valid; i++){
                            valid = table.isRelevant(playerCards[i], playerSlots[i]);
                        }
                        if(!valid){
                            players[pId].emptyHashSet();
                            players[pId].waitingToCheck = false;
                            threads[pId].interrupt();
                            players[pId].wakeAi();
                            continue;
                        }
                        
                        // (2) check if point or panelty
                        if(valid){
                            System.out.println("Dealer checks " + pId + " set: " + Arrays.toString(playerCards));
                            if(env.util.testSet(playerCards)){ //point:
                                // remove all the other players 

                                //
                                players[pId].point();
                                updateTimerDisplay(true); // rest timer
                                table.removeTokensOfPlayer(pId, playerSlots);
                                table.removeCards(playerSlots);
                                
                                //empty all other players tokens:
                                for (int otherPId = 0; otherPId < players.length; otherPId++) {
                                    if(otherPId != pId){
                                        synchronized(players[otherPId].actionsLocker){

                                            List<Integer> slotsToRemove = new LinkedList<Integer>();

                                            for(Integer[] card_slot :players[otherPId].setToCheck){
                                                if(table.slotToCard[card_slot[1]] == null)
                                                    slotsToRemove.add(card_slot[1]);
                                            }

                                            for(Integer slot:slotsToRemove){
                                                players[otherPId].removeToken(otherPId,slot);
                                            }

                                            if(!slotsToRemove.isEmpty())
                                                // table.setsToCheckQueue.remove(otherPId);
                                                // players[otherPId].waitingToCheck = false;
                                                // threads[otherPId].interrupt();
                                                players[otherPId].wakeAi();

                                            System.out.println("removing pId: " + otherPId  + " slots ");
                                        }
                                    }
                                }
                                    

                                players[pId].emptyHashSet();
                                System.out.println("player " + pId + " got a point and now the queue is: " + table.setsToCheckQueue.toString());
                            } 
                            else{ //panelty:
                                players[pId].panelty();
                                // emptyHashSet = false;
                                // System.out.println("player " + pId + " got a panelty and now the queue is: " + table.setsToCheckQueue.toString());
                            }
                        }

                        // (3) empty player's queue unless panelty
                        // if(emptyHashSet){
                        //     table.removeTokensOfPlayer(pId, playerSlots);
                        //     players[pId].emptyHashSet();
                        // }
                        players[pId].waitingToCheck = false;
                    }

                }

            // remove tokens from empty slots
            
            } catch(InterruptedException ex) {System.out.println("----dealer didn't catch dealer queue-----");}
            table.lockDealerQueue.release();
        // }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        //implement

        if(terminate)
            return;
            
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

                    try{Thread.sleep(env.config.tableDelayMillis);} 
                    catch(InterruptedException ex){ System.out.println("Dealer Interapted!");}

                }
            }
        }

        table.tableIsReady = true;
        
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
        long partial = current - startTime;
        long sleepTime = timerIntervalMills - partial % timerIntervalMills;

        System.out.println("Before sleepTime: - " + sleepTime);

        try{Thread.sleep(sleepTime);} 
        catch(InterruptedException ex){ System.out.println("Dealer Interapted!");}

        // System.out.println("After partial: - " + (timerIntervalMills - (current - startTime)% timerIntervalMills));
        
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // implement

        if(terminate)
            return;

        long current = System.currentTimeMillis();

        if(reset){
            startTime = current;
        }        

        warn = current > startTime + env.config.turnTimeoutMillis - env.config.turnTimeoutWarningMillis ;
        
        
        // if(warn)
        //     timerIntervalMills = 10;

        long partial = current - startTime;

        // if(!warn)
        //     env.ui.setCountdown(env.config.turnTimeoutMillis - partial + 999, warn);
        // else
        //     env.ui.setCountdown(env.config.turnTimeoutMillis - partial + 10, warn);

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

        table.tableIsReady = false;

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
                        players[pId].setToCheck.clear();
                        players[pId].needToRemoveToken = false;
                        System.out.println("player " + pId+ "set to check cleared the set now is: ");
                        players[pId].printSetToCheck();

                    }
                }
        } catch(InterruptedException ex) {System.out.println("----dealer didn't catch dealer queue-----");}

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

    public Thread getThread()
    {
        return mThread;
    }

}



