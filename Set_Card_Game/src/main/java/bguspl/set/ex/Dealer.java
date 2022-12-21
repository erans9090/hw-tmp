package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    protected final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    protected final List<Integer> deck;
    

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time of the loop between dealer needs to reshuffle the deck.
     */

    /**
     * should the warn on the timer be turned on
     */
    private boolean warn;

    
    /**
     * the timer interval time secion
     */
    private int timerIntervalMills = 1000;


    /**
     * the start time secion of each iteration
     */
    protected long startTime;


    /**
     * the dealers thread
     */
    private Thread mThread;


    /**
     * Array of the players threads
     */
    private Thread[] threads;


    /**
     * Constructor.
     */
    public Dealer(Env env, Table table, Player[] players) {

        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        // implemnt:
        terminate = false;
        warn = false;

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

        //intilaize the dealer thread:
        mThread = Thread.currentThread();

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
        while (!shouldFinish()) 
        {
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }

        for(int i = players.length - 1; i >= 0;i--){
            players[i].terminate();
            players[i].playerThread.interrupt();
            while(players[i].playerThread.isAlive())
            {
                try { 
                    players[i].playerThread.join();
                } 
                catch (InterruptedException ignored) {
                }
            }
        }

        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }


    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() - startTime <=  + env.config.turnTimeoutMillis ) 
        {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }


    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
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

        try{
            if(terminate)
                return;

            table.lockDealerQueue.acquire();
            while(!table.setsToCheckQueue.isEmpty())
            {
                int pId = table.setsToCheckQueue.poll();
                synchronized(players[pId].actionsLocker){

                    //if set to check size smaller then 3 empty it:
                    //happens when other player got a point and the card removed
                    if(players[pId].setToCheck.size() < 3){
                        players[pId].emptyHashSet();
                        players[pId].waitingToCheck = false;
                        // threads[pId].interrupt();
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
                        if(env.util.testSet(playerCards)){ //point:
                            // remove all the other players 

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
                                            players[otherPId].wakeAi();
                                    }
                                }
                            }
                            players[pId].emptyHashSet();

                        } 
                        else{ //panelty:
                            players[pId].panelty();

                        }
                    }
                    players[pId].waitingToCheck = false;
                }

            }        
        } catch(InterruptedException ex) {}
        table.lockDealerQueue.release();
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    protected void placeCardsOnTable() {
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
                    catch(InterruptedException ex){}

                }
            }
        }

        table.tableIsReady = true;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {

        if(warn)    // by default 1000 miliseconds / 10 miliseconds if warn
            timerIntervalMills = 10;
        else
            timerIntervalMills = 1000;
        
        long current = System.currentTimeMillis();
        long partial = current - startTime;
        long sleepTime = timerIntervalMills - partial % timerIntervalMills;

        try{Thread.sleep(sleepTime);} 
        catch(InterruptedException ex){}        
    }



    /**
     * Reset and/or update the countdown and the countdown display.
     */
    protected void updateTimerDisplay(boolean reset) {

        if(terminate)
            return;

        long current = System.currentTimeMillis();

        if(reset){
            startTime = current;
        }        

        warn = current > startTime + env.config.turnTimeoutMillis - env.config.turnTimeoutWarningMillis ;
        long partial = current - startTime;

        if(!warn)
            env.ui.setCountdown(env.config.turnTimeoutMillis - partial + 999, warn);
        else
            env.ui.setCountdown(env.config.turnTimeoutMillis - partial + 10, warn);

    }



    /**
     * Returns all the cards from the table to the deck.
     */
    protected void removeAllCardsFromTable() {
        table.tableIsReady = false;
        env.ui.setCountdown(0, true);

        if(terminate)
            return;

        if(env.config.turnTimeoutMillis < 0)
            if(env.util.findSets(deck, 1).size() != 0)
                return;

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
                        // players[pId].playerThread.interrupt();
                        players[pId].waitingToCheck = false;
                        players[pId].wakeAi();
                    }
                }
        } catch(InterruptedException ex) {}

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

    /**
     * returns the dealer thread
     */
    public Thread getThread()
    {
        return mThread;
    }

}



