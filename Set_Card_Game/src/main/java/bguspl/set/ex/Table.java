package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.LinkedList;

/**
 * This class contains the data that is visible to the player.
 * 
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    protected final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)


     // implement

    /**
     * Queue of the players that have a full set ready for being check by the diller 
     */
    public Queue<Integer> setsToCheckQueue;

    // protected Object queueLocker;

    public Semaphore lockDealerQueue;

    protected Object lock;

    protected volatile boolean tableIsReady;


    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;

        setsToCheckQueue = new LinkedList<>();
        lockDealerQueue = new Semaphore(1, true);
        lock = new Object();
        tableIsReady = false;

    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        //implement
        if (slotToCard[slot]!= null){
            cardToSlot[slotToCard[slot]] = null;
            slotToCard[slot] = null;
            env.ui.removeCard(slot);
        }

    }

    /**
     * Removes all cards in slots[]
     */
    public void removeCards(int[] slots){
        for(int s : slots)
            removeCard(s);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {

        //implement
        env.ui.placeToken(player, slot);
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        // implement
        env.ui.removeToken(player, slot);
        return true;
    }

    /**
     * Removes all the token from a grid slot.
     */
    public void removeAllTokens(){
        env.ui.removeTokens();
    }

    /**
     * Removes all the token from a grid slot.
     */
    public void removeTokensOfPlayer(int pId, int[] slots){
        for(int s : slots)
            removeToken(pId, s);
    }

    /**
     * Checks if the card as the player picked it is still relavant and hasn't changed cause of the dealer actions
     * @param card   - the card we check
     * @param slot   - the slot we check
     * @return       - true if the combination is still relavant
     *               - false if the combination is not relavant
     */
    public boolean isRelevant(Integer card, int slot){
        if (card == null)
            return false;
        return (slotToCard[slot] != null && cardToSlot[card] != null && cardToSlot[card].equals(slot));
    }

}
