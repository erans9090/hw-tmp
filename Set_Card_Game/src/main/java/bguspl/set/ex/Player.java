package bguspl.set.ex;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import java.util.Arrays;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    protected final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    protected Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    public final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    protected volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    // implement

    /**
     * a queue of the incoming actions - key pressed translated to slots locations
     */
    protected Queue<Integer[]> incomingActions;

    /**
     * Indicate the remaine freeze time after pelenty or point
     */
    protected long frozenTimer;

    /**
     * every change in actionsLocker and in setToCheck need to use this locker
     * basically this lock synchronize the token the player holds and want to place/remove
     */
    protected Object actionsLocker;

     /**
      * the dealer
      */
    private Dealer dealer;

    /**
     * holds the current placed tokens([card][slot])
     * @size is 3*2
     */
    protected Set<Integer[]> setToCheck;

    /**
     * boolean to indicates rather this player waiting for the dealer to check his set
     */
    protected boolean waitingToCheck;

    /**
     * the time between the ai pushes keys
     */
    private final long aiSleepBetweenPushes = 0;

    private boolean startAgainTheLoop;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;

        // implement
        this.dealer = dealer;
        this.incomingActions = new LinkedList<Integer[]>();
        this.setToCheck = new HashSet<Integer[]>(3);
        this.frozenTimer = -1000;
        this.terminate = false;
        this.actionsLocker = new Object();
        this.waitingToCheck = false;
        this.startAgainTheLoop = false;

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {

        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        
        // deals threads order  -> 
        synchronized(table.lock){
            if (!human) {
                createArtificialIntelligence();
                try{table.lock.wait();}
                catch(InterruptedException ex){}
            }
            else
                table.lock.notifyAll();
        }
        //   <-
        

        //--- main loop player ---//
        while (!terminate) {

            waitUntilWoken();
            updateFreezeTimeDisplay();
            manageInput();            
        }
        //--- main loop player ---//

        // close ai thread before terminting ->
        if (!human){
            aiThread.interrupt();
            while(aiThread.isAlive()){
                try {
                    aiThread.join(); }
                catch (InterruptedException ignored) {
                aiThread.interrupt();
                }
            }
        }
        // <-

        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * update frozen timer of player and update display
     */
    private void updateFreezeTimeDisplay(){
        
        // dont freeze if need to terminate
        if(terminate)
            return;
                    
        while(frozenTimer >= 0 && !terminate){

            env.ui.setFreeze(id, frozenTimer);
            frozenTimer -= 1000;
            
            try{Thread.sleep(1000);}
            catch(InterruptedException ex){}
                if(frozenTimer < 0)
                    if(!human)
                        aiThread.interrupt();
                    
        }
    }

    /**
     * consume actions from keyPress and deal them 
     */
    private void manageInput(){

        if(terminate)
            return;

        boolean needToAdd = false;

        synchronized(actionsLocker){

            while(!incomingActions.isEmpty() && !terminate){
                
                // consume action
                Integer[] card_slot = incomingActions.poll();

                // remove if alredy have this slot 
                if(isSlotInSetToCheck(card_slot[1])){
                    
                    removeToken(card_slot[0],card_slot[1]);
                    
                }
                // make sure adding is valid and safe
                else if(card_slot[0] != null && table.isRelevant(card_slot[0], card_slot[1]) && setToCheck.size() < 3){
 
                    setToCheck.add(card_slot);
                    table.placeToken(id,card_slot[1]);
                    if(setToCheck.size() == 3){
                        needToAdd = true;
                        waitingToCheck = true;       
                    }
                }
            }
        }
        // if player pressed 3 valid tokens send the set to the dealer
        if(needToAdd && !terminate){
            boolean catchDealerQueue = false;
            while(!catchDealerQueue && !terminate){
                try{
                    table.lockDealerQueue.acquire();

                    table.setsToCheckQueue.add(id);
                    
                    System.out.println("player " + id + " interapted the sweet dealer sleep and now dealers queue: ");
                    // System.out.println("<- THE QUEUE IS ->");
                    // for (Integer pId : table.setsToCheckQueue) {
                    //     System.out.println(pId);
            
                    // }
                    dealer.getThread().interrupt();
                    table.lockDealerQueue.release();
                    catchDealerQueue = true;
                }
                catch(InterruptedException ex) {}
            }
        }
    }
        
    
    /**
     * waits untill need to wake up
     */
    private void waitUntilWoken(){
        if(terminate)
            return;
        
        if(startAgainTheLoop)
            aiThread.interrupt();
        try{System.out.println("player " + id + " is waiting"); 
            Thread.sleep(env.config.turnTimeoutMillis);}
        catch(InterruptedException ex){System.out.println("player " + id + " is STOP waiting"); }
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {

        aiThread = new Thread(() -> {
            
            //ARTIFICAL INTELLEGENCE:

            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            
            synchronized(table.lock){
                table.lock.notifyAll();
            }

            // -- main loop --//
            while (!terminate) {
                while(!table.tableIsReady && !terminate){
                        try{ Thread.sleep((10));}
                        catch(InterruptedException ex){}
                    }

                startAgainTheLoop = false;
                
                //clear the old cards:
                synchronized(actionsLocker){
                    for(Integer[] card_slot : setToCheck)
                        table.removeToken(id, card_slot[1]);
                    incomingActions.clear();
                    setToCheck.clear();
                }

                //get the avilabel cards that on the table:
                List<Integer> cards = new LinkedList<>();
                for (Integer card : table.slotToCard) {
                    if(card != null)
                        cards.add(card);
                }

                //find sets in the cards
                List<int[]> sets = env.util.findSets(cards,1);

                //decide if to send a set or random
                Random r = new Random();
                int chance = r.nextInt(3);

                //send a set:
                if (chance == 0 && sets.size() > 0){
                    for (int card : sets.get(0)) {
                        //sleep for a sort time before each key press
                        try{Thread.sleep(aiSleepBetweenPushes);}
                        catch(InterruptedException ex){}
                        if(terminate){
                            startAgainTheLoop = true;
                            break;
                        }
                        
                        //press key:
                        Integer slot = table.cardToSlot[card];
                        if(slot != null){
                            keyPressed(slot.intValue());
                        }
                        else{
                            startAgainTheLoop = true;
                            break;
                        }

                    }
                } //send a random guess:
                else{

                    Integer[] guess = {r.nextInt(11),r.nextInt(11),r.nextInt(11)};
                    // make sure the slot are not the same
                    while(guess[1] == guess[0])
                        guess[1] = r.nextInt(11);
                    while(guess[2] == guess[1] || guess[2] == guess[0])
                        guess[2] = r.nextInt(11);

                    // if the dealer already removed this slot skip this iteration and start all over again
                    if(table.slotToCard[guess[0]] == null || table.slotToCard[guess[1]] == null || table.slotToCard[guess[2]] == null)
                        continue;
                    
                    // send slots
                    for (Integer slot : guess) {
                        keyPressed(slot);
                        if(terminate)break;
                        try{Thread.sleep(aiSleepBetweenPushes);}
                        catch(InterruptedException ex){}
                    }


                }

                //for case of terminate:
                if(startAgainTheLoop)
                    continue;

                
                //wait untill next key press needed:
                synchronized(aiThread){
                    try{System.out.println("AI " + id + " is waiting"); 
                        aiThread.wait(); }
                    catch( InterruptedException ex ){
                    }
                    System.out.println("AI " + id + " is STOP waiting"); 
                }
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }
    


    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        aiThread.interrupt();
    }

    
    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     * @post incomingActions.contains(slot,table.slotToCard[slot])
     */
    public void keyPressed(int slot) {

        // implement
      
        //   cases keyPressed cant be excecuted - dont take presses:
        //      - if player is in panelty/got point
        //      - if the card has removed from the slot or changed
        //      - if player queue is full
        if(frozenTimer > 0 || table.slotToCard[slot] == null || incomingActions.size() >= 3){
            startAgainTheLoop = true;
            System.out.println("key pressed for player " + id + " with slot " + slot + " but FORZEN OR OTHER");
            return;

        }

        synchronized(actionsLocker){
            
            // if not dealers queue, add the key press to incoming actions
            if(!waitingToCheck){
                Integer[] tmp = {table.slotToCard[slot],slot};
                incomingActions.add(tmp);
                synchronized(playerThread){
                playerThread.interrupt();}
                System.out.println("key pressed and player interapter for player " + id + " with slot " + slot + " AND ADDED");
            }

                        
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        frozenTimer = env.config.pointFreezeMillis;
        score++;
        env.ui.setScore(id, score);
        if(playerThread != null)
            playerThread.interrupt();
    }


    /**
     * Penalize a player and perform other related actions.
     * 
     * @post - the player's frozenTimer is increased to the num at config file.
     */
    public void panelty() {
        frozenTimer = env.config.penaltyFreezeMillis;
        if(playerThread != null)
            playerThread.interrupt();
    }


    /*
     * returns the score of the player
     */
    public int getScore() {
        return score;
    }

     /**
      *  @pre incomingActions.length == 3
      *  @post incomingActions.length == 0
      * @return 
      */
    public int[][] getSetFromHahSet(){ // CRITICAL SECTION dealer has the key therefore it is synchronized

        int[][] ans = new int[3][2];
        int i = 0;
        for(Integer[] card_slot : setToCheck){
            ans[i][0] = card_slot[0];
            ans[i][1] = card_slot[1];
            i += 1;
        }
        return ans;
    }

    /*
     * empty the hash hash set and clear the incoming actions
     */
    public void emptyHashSet(){ // CRITICAL SECTION dealer has the key therefore it is synchronized
        incomingActions.clear();
        setToCheck.clear();
    }
    
    /*
     * check if slot is in the set to check queue
     */
    private boolean isSlotInSetToCheck(int slot)
    {
        for(Integer[] i : setToCheck)
            if(i[1] == slot)
                return true;
        return false;
    }


    /*
     * prints set to check
     */
    public String printSetToCheck()
    {
        String ans = "[";
        for (Integer[] cardslot: setToCheck) {
            ans += Arrays.toString(cardslot) + ",";
        }
        return ans.substring(0, ans.length()) + "]";
    }

    /*
     * remove a token from a player
     * remove from queue
     * remove from ui
     */
    protected boolean removeToken(int card, int slot){
        for (Integer[] set : setToCheck) {
            if (slot == set[1]){

                setToCheck.remove(set);
                table.removeToken(id,slot);
                return true;

            }
        }
        return false;
    }

    /*
     * a wake the ai
     */
    protected void wakeAi(){
        if(!human){
            // synchronized(aiThread){
                waitingToCheck = false;
                aiThread.interrupt();
            // }
        }
    } 
}