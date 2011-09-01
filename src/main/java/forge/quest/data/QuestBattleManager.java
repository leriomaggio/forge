package forge.quest.data;

import forge.AllZone;
import forge.FileUtil;
import forge.deck.Deck;
import forge.deck.DeckManager;
import forge.error.ErrorViewer;
import forge.properties.ForgeProps;
import forge.properties.NewConstants;

import java.io.File;
import java.util.*;

/**
 * <p>QuestBattleManager class.</p>
 *
 * @author Forge
 * @version $Id$
 */
public class QuestBattleManager {
    /** Constant <code>easyAIDecks</code> */
    private static transient List<String> easyAIDecks;
    /** Constant <code>mediumAIDecks</code> */
    private static transient List<String> mediumAIDecks;
    /** Constant <code>hardAIDecks</code> */
    private static transient List<String> hardAIDecks;
    /** Constant <code>veryHardAIDecks</code> */
    private static transient List<String> veryHardAIDecks;

    static {
        easyAIDecks = readFile(ForgeProps.getFile(NewConstants.QUEST.EASY));
        mediumAIDecks = readFile(ForgeProps.getFile(NewConstants.QUEST.MEDIUM));
        hardAIDecks = readFile(ForgeProps.getFile(NewConstants.QUEST.HARD));
        veryHardAIDecks = readFile(ForgeProps.getFile(NewConstants.QUEST.VERYHARD));
    }

    /**
     * <p>getAIDeckNames.</p>
     *  Returns a list of decks stored in the
     *  {@link forge.quest.gui.main.aiDecks} map. 
     *
     * @return a {@link java.util.List} object.
     */
    public static List<String> getAIDeckNames() {
        return new ArrayList<String>(aiDecks.keySet());
    }

    /**
     * <p>getOpponent.</p>
     *
     * Badly named; AllZoneUtil already has a method called getOpponents.
     * ?????
     *
     * @param aiDeck a {@link java.util.List} object.
     * @param number a int.
     * @return a {@link java.lang.String} object.
     */
    public static String getOpponent(List<String> aiDeck, int number) {
        //This is to make sure that the opponents do not change when the deck editor is launched.
        List<String> deckListCopy = new ArrayList<String>(aiDeck);
        Collections.shuffle(deckListCopy, new Random(AllZone.getQuestData().getRandomSeed()));

        return deckListCopy.get(number);
    }


    /**
     * <p>generateBattles.</p>
     * Generates an array of new opponents based on current win conditions.
     *
     * @return an array of {@link java.lang.String} objects.
     */
    public static String[] generateBattles() {
        int index = AllZone.getQuestData().getDifficultyIndex();

        if (AllZone.getQuestData().getWin() < QuestPreferences.getWinsForMediumAI(index)) {
            return new String[]{
                    getOpponent(easyAIDecks, 0),
                    getOpponent(easyAIDecks, 1),
                    getOpponent(easyAIDecks, 2)};
        }

        if (AllZone.getQuestData().getWin() == QuestPreferences.getWinsForMediumAI(index)) {
            return new String[]{
                    getOpponent(easyAIDecks, 0),
                    getOpponent(mediumAIDecks, 0),
                    getOpponent(mediumAIDecks, 1)};
        }

        if (AllZone.getQuestData().getWin() < QuestPreferences.getWinsForHardAI(index)) {
            return new String[]{
                    getOpponent(mediumAIDecks, 0),
                    getOpponent(mediumAIDecks, 1),
                    getOpponent(mediumAIDecks, 2)};
        }

        if (AllZone.getQuestData().getWin() == QuestPreferences.getWinsForHardAI(index)) {
            return new String[]{
                    getOpponent(mediumAIDecks, 0),
                    getOpponent(hardAIDecks, 0),
                    getOpponent(hardAIDecks, 1)};
        }

        if (AllZone.getQuestData().getWin() >= QuestPreferences.getWinsForVeryHardAI(index)) {
            return new String[]{
                    getOpponent(hardAIDecks, 0),
                    getOpponent(hardAIDecks, 1),
                    getOpponent(veryHardAIDecks, 0)};
        }

        return new String[]{
                getOpponent(hardAIDecks, 0),
                getOpponent(hardAIDecks, 1),
                getOpponent(hardAIDecks, 2)};
    }

    /**
     * <p>readFile.</p>
     * A reader util for accessing the AI deck list text files.
     *
     * @param file a {@link java.io.File} object.
     * @param aiDecks a {@link java.util.List} object.
     * @return a {@link java.util.List} object.
     */
    private static List<String> readFile(File file) {
        ArrayList<String> list = FileUtil.readFile(file);

        //remove any blank lines
        ArrayList<String> noBlankLines = new ArrayList<String>();
        String s;
        for (String aList : list) {
            s = aList.trim();
            if (!s.equals("")) {
                noBlankLines.add(s);
            }
        }
        list = noBlankLines;

        if (list.size() < 3) {
            ErrorViewer.showError(new Exception(),
                    "QuestData : readFile() error, file %s is too short, it must contain at least 3 ai deck names",
                    file);
        }

        return list;
    }

}
