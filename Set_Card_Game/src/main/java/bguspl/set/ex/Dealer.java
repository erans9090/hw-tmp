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

// import java.util.Queue;
// import java.util.Arrays;
// import java.util.LinkedList;

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
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * Indicate the num of the cards in the table - usefull when the deck is pver and we want to check if there any liggal sets left
     */
    // private int nunOfCardsOnTable;

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
        // nunOfCardsOnTable = 12;
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
            reshuffleTime = 10000;
            env.ui.setCountdown(reshuffleTime, false);
            
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
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
        // implement
        removeAllCardsFromTable();
        for(int i = 0; i < players.length;i++){
            players[i].terminate();
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        if(env.util.findSets(deck, 1).size() == 0)
            terminate();
        return terminate;
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable() {
        
        // implement

        // CRITICAL SECTION dealer should not be interupted
        //  until a set is found or the queue is empty
        synchronized(table.queueLocker){
            while(!table.setsToCheckQueue.isEmpty()){
                int pId = table.setsToCheckQueue.poll();
                System.out.println("check player " + pId + " and now the queue is: " + table.setsToCheckQueue);
                synchronized(players[pId].actionsLocker){
                    int[][] playerSet = players[pId].getSetFromQueue();
                    int[] playerCards = new int[3];
                    int[] playerSlots = new int[3];
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
                            reshuffleTime = 10000;
                            env.ui.setCountdown(reshuffleTime, false);

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
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        //implement
        
        if(!hasSetInGame())
            terminate();
        else{
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
    }

    /**
     * Check if there is a set in the game
     */
    private boolean hasSetInGame() {
        LinkedList<Integer> cards = new LinkedList<Integer>();

        for(int card : deck) 
            cards.add(card);

        for (int slot = 0; slot < 12; slot++ )
            if (table.slotToCard[slot] != null)
                cards.add(table.slotToCard[slot]);
        
        //check if there is a set:
        List<int[]> temp = env.util.findSets(cards, 1);
        if(env.util.findSets(cards, 1).size() == 0){
            System.out.println("No more sets avalible!");
            return false;
        }
        return true;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // implement

        try {
            Thread.sleep(950);
        } 
        catch(InterruptedException ex){}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // implement
        reshuffleTime -= 1000;
        env.ui.setCountdown(reshuffleTime, false);

        for (int i = 0; i < players.length; i++) {
            players[i].updateFreezeTime();
        }
        
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {

        // System.out.printf("removeAllCardsFromTable Loop");

        // implement

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
        synchronized(table.queueLocker){
            table.setsToCheckQueue.clear();
            for (int pId = 0 ; pId< players.length ; pId++){
                synchronized(players[pId].actionsLocker){
                        players[pId].incomingActions.clear();
                    }
                }
        }
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
