package bguspl.set.ex;

import java.util.HashSet;
import java.util.LinkedList;
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
    private final Env env;

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
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    // implement

    /**
     * a queue of the incoming actions - key pressed translated to slots locations
     */
    public Queue<Integer[]> incomingActions;

     /**
     * Indicate the remaine freeze time after pelenty or point
     */
    protected long frozenTimer;

    protected Object actionsLocker;

    /**
     * chnges every iteration to notifiy when display change needs to be done 
     */
    // private int countUpdate;

    /**
     * @true - if the dealer put you in palenty and you have to move a card
     */
    private boolean needToMoveCardBecausePanelty;

     /**
     * the dealer
     */
    private Dealer dealer;

    /**
     * holds the current placed tokens([card][slot])
     * @size is 3*2
     */
    protected Set<Integer[]> setToCheck;

    protected boolean needToRemoveToken;


    

    


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
        // System.out.println("in player " + id + " const : " + dealerThread);
        incomingActions = new LinkedList<Integer[]>();
        frozenTimer = -1000;
        terminate = false;
        actionsLocker = new Object();
        needToMoveCardBecausePanelty = false;
        setToCheck = new HashSet<Integer[]>(3);
        needToRemoveToken = false;
        // countUpdate =- 1;

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        
        // deals threads order 
        synchronized(table.lock){
            if (!human) {
                createArtificialIntelligenceSmart();
                try{table.lock.wait();}
                catch(InterruptedException ex){}
            }
            else
                table.lock.notifyAll();
        }

        //--- main loop player ---//
        while (!terminate) {

            updateFreezeTimeDisplay();
            manageInput();
            waitUntilWoken();
            
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    private void updateFreezeTimeDisplay(){
                    
        while(frozenTimer >= 0 && !terminate){

            if(terminate)
                break;
            env.ui.setFreeze(id, frozenTimer);
            frozenTimer -= 1000;

            try{ Thread.sleep(1000); }
            catch(InterruptedException ex){}
        }
    }

    private void manageInput(){

        synchronized(actionsLocker){
            while(!incomingActions.isEmpty()){
                

                Integer[] card_slot = incomingActions.poll();
                System.out.println("player: "+ id + " got card_slot: " + Arrays.toString(card_slot) + " and now set is: " + printSetToCheck());

                if(incomingActions.isEmpty())System.out.println("actions Queue is empty");
                // System.out.println("is setToCheck contains card_slot");
                // System.out.println(isSlotInSetToCheck(card_slot[1]));

                if(isSlotInSetToCheck(card_slot[1])){
                    

                    System.out.println("is the set removed: " + removeToken(card_slot[0],card_slot[1]));
                    System.out.println("player: " + id + " remove card_slot: " + Arrays.toString(card_slot) + " and now set is: " + printSetToCheck());
                    
                }
                else if(table.isRelevant(card_slot[0], card_slot[1]) && setToCheck.size() < 3){
 
                    setToCheck.add(card_slot);
                    table.placeToken(id,card_slot[1]);
                    System.out.println("player: "+ id + " add card_slot: " + Arrays.toString(card_slot) + " and now set is: " + printSetToCheck());
                }
                else{
                    System.out.println("player: "+ id + " denayed card_slot: " + Arrays.toString(card_slot) + " and now set is: " + printSetToCheck());

                }
                
                if(setToCheck.size() == 3){
                    try{
                        table.lockDealerQueue.acquire();
                        
                        table.setsToCheckQueue.add(id);
                        System.out.println("set added!!");

                        needToRemoveToken = true;
                        dealer.getThread().interrupt();
                    }
                    catch(InterruptedException ex) {System.out.println("----didn't catch dealer queue-----");}
                    table.lockDealerQueue.release();
                }
            }
        }
        
    }

    private synchronized void waitUntilWoken(){
        try{ wait(); }
        catch(InterruptedException ex){}
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                // implement player key press simulator
                Random r = new Random();
                keyPressed(r.nextInt((int)(env.config.rows*env.config.columns))); 
                try{
                    Thread.sleep(500);
                }catch(InterruptedException ex){}
                // try {
                //     synchronized (this) { wait(); }
                // } catch (InterruptedException ignored) {}
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    private void createArtificialIntelligenceSmart() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            synchronized(table.lock){
                table.lock.notifyAll();
            }

            try{Thread.sleep(4000);} 
            catch(InterruptedException ex){ System.out.println("player: " + id+ " ai Interapted!");}
            System.out.println("player: " + id+ " ai started!");

            while (!terminate) {
                // implement player key press simulator

                if(needToMoveCardBecausePanelty){ //got a panelty beacuse a wrong set - so remove all my tokens first
                    int[][] tokensToMove = getSetFromHahSet();
                    keyPressed(tokensToMove[0][1]);
                    keyPressed(tokensToMove[1][1]);
                    keyPressed(tokensToMove[2][1]);
                }
                else{ //choose new set

                    Random r = new Random();
                    Integer[] attempt = new Integer[3];
                    for (int i = 0; i < 5000; i++) {
                        attempt[0] = r.nextInt((int)(env.config.rows*env.config.columns)) ;
                        attempt[1] = r.nextInt((int)(env.config.rows*env.config.columns)) ;
                        attempt[2] = r.nextInt((int)(env.config.rows*env.config.columns)) ;

                        int[] testSet = new int[3];
                        // if(attempt[0] == null || attempt[1] == null || attempt[2] == null)
                        //     continue;
                        if(table.slotToCard[attempt[0]] != null && table.slotToCard[attempt[1]] != null && table.slotToCard[attempt[2]] != null){
                            testSet[0] = table.slotToCard[attempt[0]];
                            testSet[1] = table.slotToCard[attempt[1]];
                            testSet[2] = table.slotToCard[attempt[2]];
                        }
                        // if(testSet[0] == null || testSet[1] == null || testSet[2] == null)
                        //     continue;

                        if(env.util.testSet(testSet))
                            break;
                    }
                    System.out.println(Arrays.toString(attempt));

                    for (int i = 0; i < attempt.length; i++) {

                        if (attempt[0] == attempt[1] || attempt[0] == attempt[2] || attempt[1] == attempt[2])
                            break;
                            
                        
                        keyPressed(attempt[i]); 

                        try{
                            Thread.sleep(800);
                        }catch(InterruptedException ex){}
                    }
                }
                
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public synchronized void terminate() {
        // implement
        terminate = true;
        notifyAll();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public synchronized void keyPressed(int slot) {

        // implement

        

        // when arriving to full capacitiy(3) need to lock the insertion avoiding 
        // any input until the queue is complitly defleted
        synchronized(actionsLocker){

            System.out.println("key presed: player "+ id + " got slot " + slot);

            if(frozenTimer > 0){
                System.out.println("key presed: but frozen");
                return;}


            // keypressed

            //
            if (needToRemoveToken)
            {
                // System.out.println("key presed: need to remove");
                
                if(isSlotInSetToCheck(slot))
                {
                    // System.out.println("key presed: slot to remove -- secceed");

                    Integer[] tmp = {table.slotToCard[slot],slot};
                    incomingActions.add(tmp);
                    
                }

            }
            else if(incomingActions.size() < 3) {
                // System.out.println("key presed: incoming actions < 3");

                Integer[] tmp = {table.slotToCard[slot],slot};
                incomingActions.add(tmp);
            }
                
            // System.out.println("current thread:" + Thread.currentThread());
            // System.out.println("player thread:" + playerThread);

            notifyAll();
            return;
                 


            // //first thing - remove token if already in queue
            // Integer[] toRemove = null;
            // for(Integer[] i : incomingActions)
            //         if(i[1] != null) 
            //             //check if he pressed the card location already:
            //             if(i[1].equals(slot)){
            //                 toRemove = i;
            //             }
            
            // if(toRemove != null){
            //     incomingActions.remove(toRemove);
            //     table.removeToken(id, toRemove[1]);
            //     needToMoveCardBecausePanelty = false;
            // }
            // else{
            //     if(!table.setsToCheckQueue.contains(id)){
            //         //if didn't choose this card yet:
            //         if (incomingActions.size() < 3 && table.slotToCard[slot] != null){
            //             table.placeToken(id, slot);
            //             Integer[] toAdd = {table.slotToCard[slot], slot};
            //             incomingActions.add(toAdd);
            //             if (incomingActions.size() == 3){
            //                 try{
            //                     table.lockDealerQueue.acquire();
            //                     table.setsToCheckQueue.add(id);
            //                     dealer.getThread().interrupt();
            //                     // System.out.println("player " + id + " added to the queue: " + table.setsToCheckQueue.toString());
            //                 } catch(InterruptedException ex) {System.out.println("----didn't catch dealer queue-----");}
            //                 table.lockDealerQueue.release();
            //             }
            //         }
            //     }
            // }
                        
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public synchronized void point() {
        // implement

        frozenTimer = env.config.pointFreezeMillis;
        env.ui.setScore(id, ++score);
        needToRemoveToken = false;
        notifyAll();

    }


    /**
     * Penalize a player and perform other related actions.
     */
    public synchronized void panelty() {
        // implement
        // needToRemoveToken = true;
        needToMoveCardBecausePanelty = true;
        frozenTimer = env.config.penaltyFreezeMillis;
        notifyAll();

    }

    public int getScore() {
        return score;
    }

    // implement
    
     
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

    public void emptyHashSet(){ // CRITICAL SECTION dealer has the key therefore it is synchronized
        // incomingActions.clear();
        setToCheck.clear();
    }
    

    public synchronized void updateFreezeTime(){
        if(frozenTimer > 0){

            frozenTimer -= 10;
            
            if(frozenTimer == 0)
                frozenTimer = -1000;
            
            if(frozenTimer %1000 == 0)
                notifyAll();
        }
    }

    private boolean isSlotInSetToCheck(int slot)
    {
        for(Integer[] i : setToCheck)
            if(i[1] == slot)
                return true;
        return false;
    }

    public String printSetToCheck()
    {
        String ans = "[";
        for (Integer[] cardslot: setToCheck) {
            ans += Arrays.toString(cardslot) + ",";
        }
        return ans.substring(0, ans.length()) + "]";
    }

    private boolean removeToken(int card, int slot)
    {
        for (Integer[] set : setToCheck) {
            if (card == set[0] && slot == set[1]){
                setToCheck.remove(set);
                table.removeToken(id,slot);
                needToRemoveToken = false;
                return true;
            }
        }
        return false;
    }

    private boolean isSetToCheckContains(int card, int slot){
        for (Integer[] i : setToCheck) 
            if (card == i[0] && slot == i[1])
                return true;
        return false;
            
    }
        
    
}

