package bguspl.set.ex;

import bguspl.set.Env;

import java.nio.channels.Pipe;
import java.util.Arrays;
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
    private int nunOfCardsOnTable;

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
        nunOfCardsOnTable = 12;
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
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable() {
        
        // implement

        // CRITICAL SECTION dealer should not be interupted
        //  until a set is found or the queue is empty
        synchronized(table.queueLocker){
        for (int pId: table.setsToCheckQueue){
            synchronized(players[pId].actionsLocker){
            int[][] playerSet = players[pId].getSetFromQueue();
            int[] playerCards = new int[3];
            for(int i = 0; i<3; i++)
                playerCards[i] = playerSet[i][0];

            for(int i = 0 ; i< playerSet.length;i++)
                    if(table.cardToSlot[playerSet[i][0]] != playerSet[i][0]){
                        int[] playerSlots = new int[3];
                        for(int j = 0; j<3; j++)
                            playerSlots[j] = playerSet[j][1];
                        // table.removeTokensOfPlayer(pId, playerSlots);
                        table.removeAllTokens();
                        System.out.println("board has changed!");
                    }
            
            System.out.println("Dealer checks " + pId + " set: " + Arrays.toString(playerCards));
            
            if(env.util.testSet(playerCards)){
                for(int i = 0 ; i< playerSet.length;i++)
                    table.removeCard(table.cardToSlot[playerCards[i]]);
                
                
                players[pId].point();
                // players[pId].incomingActions.clear();
                table.removeAllTokens();
                table.setsToCheckQueue.clear();
                System.out.println("player " + pId + " got a point and now the queue is: " + table.setsToCheckQueue.toString());
                break;
            }
            else{
                // remove player wrong set tokens
                players[pId].penalty();
                for(int i = 0 ; i< playerSet.length;i++)
                    table.removeToken(pId,table.cardToSlot[playerCards[i]]);
                table.setsToCheckQueue.remove();
                System.out.println("player " + pId + " got a panelty and now the queue is: " + table.setsToCheckQueue.toString());

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
        
        //shuffel 12 cards from deck to the board
        for (int slot = 0; slot < 12; slot++ ){
            if (table.slotToCard[slot] == null){
                if(deck.size() > 0){
                    Random r = new Random();
                    int cardIndex = r.nextInt(deck.size());
                    int cardId = deck.get(cardIndex);
                    deck.remove(cardIndex);
                    table.placeCard(cardId, slot);
                }
                else{
                    //if the deck is empty, don't draw a card,
                    //insead, check if there is a liggal set on the table, if not - terminate
                    nunOfCardsOnTable -= 1;
                    int index = 0;
                    int[] cardsOnTable = new int[nunOfCardsOnTable];
                    for (int s = 0; s < 12; s++ ){
                        if (table.slotToCard[slot] != null){
                            cardsOnTable[index] = table.slotToCard[slot];
                            index += 1;
                        }
                    }
                    if(env.util.testSet(cardsOnTable) == false)
                        terminate();
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // implement

        try {
            Thread.sleep(1000);
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

        for (int slot = 0; slot < 12; slot++ ){
            deck.add(table.slotToCard[slot]);
            table.removeCard(slot);
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

    /**
     * Clear all player incoming actions
     * Call table for remove all the token from a grid slot.
     */
    public void removeAllTokens(){ // Not working !! need to change!
        for( int i = 0; i < 12;i++)
            players[i].incomingActions.clear();
        table.removeAllTokens();
    }
}
