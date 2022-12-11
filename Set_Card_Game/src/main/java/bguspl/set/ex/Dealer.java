package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Arrays;
import java.util.Collections;
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
    private long reshuffleTime = Long.MAX_VALUE;


    /**
     * The time of the loop between dealer needs to reshuffle the deck.
     */
    private final int loopTime = 10000;

    /**
     * should the warn on the timer be turned on
     */
    private boolean warn;


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
        terminate = false;
        warn = false;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        
        // start players threads
        threads = new Thread[players.length];
        for(int i = 0; i < players.length;i++){
            threads[i] = new Thread(players[i]);
            threads[i].start();
        }

        while (!shouldFinish()) {
            placeCardsOnTable();

            //set timer:
            reshuffleTime = loopTime;
            warn = false;
            env.ui.setCountdown(reshuffleTime, false);
            
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }

        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }


    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && reshuffleTime > 0) {
            sleepUntilWokenOrTimeout();
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
        for(int i = 0; i < players.length;i++){
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
        // synchronized(table.queueLocker){
        try{
            table.lockDealerQueue.acquire();
        } catch(InterruptedException ex) {System.out.println("----didn't catch dealer queue-----");}
        while(!table.setsToCheckQueue.isEmpty()){
            int pId = table.setsToCheckQueue.poll();
            System.out.println("check player " + pId + " and now the queue is: " + table.setsToCheckQueue);
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
                    System.out.println("Dealer checks " + pId + " set: " + Arrays.toString(playerCards));
                    
                    //got a point:
                    if(env.util.testSet(playerCards)){
                        for(int i = 0 ; i< playerSet.length;i++){
                            table.removeToken(pId,table.cardToSlot[playerCards[i]]);
                            table.removeCard(table.cardToSlot[playerCards[i]]);
                        }
                        
                        players[pId].point();

                        //reset timer:
                        reshuffleTime = loopTime;
                        warn = false;
                        env.ui.setCountdown(reshuffleTime, warn);

                        System.out.println("player " + pId + " got a point and now the queue is: " + table.setsToCheckQueue.toString());
                    
                    } //panelty:
                    else{
                        // remove player wrong set tokens
                        players[pId].penalty();
                        for(int i = 0 ; i< playerSet.length;i++)
                            table.removeToken(pId,table.cardToSlot[playerCards[i]]);
                        System.out.println("player " + pId + " got a panelty and now the queue is: " + table.setsToCheckQueue.toString());

                    }
                }
            }  
        }
        table.lockDealerQueue.release();
        // }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        //implement

        //shuffel 12 cards from deck to the board
        Integer [] arr = {0,1,2,3,4,5,6,7,8,9,10,11};
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

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // implement

        if(reshuffleTime <= 5000)
            warn = true;

                
        reshuffleTime -= 10;
        // env.ui.setCountdown(reshuffleTime, warn);
        try {
            Thread.sleep(10);
        } 
        catch(InterruptedException ex){}
            
        
        // else{
        //     try {
        //         Thread.sleep(950);
        //     } 
        //     catch(InterruptedException ex){}
        // }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // implement
        env.ui.setCountdown(reshuffleTime, warn);

        for (int i = 0; i < players.length; i++) {
            players[i].updateFreezeTime();
        }
        
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // implement

        if(terminate)
            return;

        System.out.println("reshuffle!");

        Integer [] arr = {0,1,2,3,4,5,6,7,8,9,10,11};
        List<Integer> lst = Arrays.asList(arr);
        Collections.shuffle(lst);

        for (int slot :lst){
            if (table.slotToCard[slot] != null){
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }
        }
        // synchronized(table.queueLocker){
        try{
            table.lockDealerQueue.acquire();
        } catch(InterruptedException ex) {System.out.println("----didn't catch dealer queue-----");}
        table.setsToCheckQueue.clear();
        for (int pId = 0 ; pId< players.length ; pId++){
            synchronized(players[pId].actionsLocker){
                    players[pId].incomingActions.clear();
                }
            }
        table.lockDealerQueue.release();
        // }
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
