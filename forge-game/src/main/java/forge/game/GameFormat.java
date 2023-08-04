/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.game;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import forge.StaticData;
import forge.card.CardDb;
import forge.card.CardEdition;
import forge.card.CardEdition.CardInSet;
import forge.card.CardRarity;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.util.FileSection;
import forge.util.FileUtil;
import forge.util.storage.StorageBase;
import forge.util.storage.StorageReaderRecursiveFolderWithUserFolder;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;


public class GameFormat implements Comparable<GameFormat> {
    private final String name;

    public enum FormatType {
        SANCTIONED,
        CASUAL,
        ARCHIVED,
        DIGITAL,
        CUSTOM
    }

    public enum FormatSubType {
        BLOCK,
        STANDARD,
        EXTENDED,
        PAUPER,
        PIONEER,
        MODERN,
        LEGACY,
        VINTAGE,
        COMMANDER,
        PLANECHASE,
        VIDEOGAME,
        MTGO,
        ARENA,
        CUSTOM
    }

    // contains allowed sets, when empty allows all sets
    private FormatType formatType;
    private FormatSubType formatSubType;

    protected final List<String> allowedSetCodes; // this is mutable to support quest mode set unlocks
    protected final List<CardRarity> allowedRarities;
    protected final List<String> bannedCardNames;
    protected final List<String> restrictedCardNames;
    protected final List<String> additionalCardNames; // for cards that are legal but not reprinted in any of the allowed Sets
    protected boolean restrictedLegendary = false;
    private Date effectiveDate;
    private final static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    private final static String DEFAULTDATE = "1990-01-01";

    protected final transient List<String> allowedSetCodes_ro;
    protected final transient List<String> bannedCardNames_ro;
    protected final transient List<String> restrictedCardNames_ro;
    protected final transient List<String> additionalCardNames_ro;

    protected final transient Predicate<PaperCard> filterRules;
    protected final transient Predicate<PaperCard> filterPrinted;

    private final int index;

    public GameFormat(final String fName, final Iterable<String> sets, final List<String> bannedCards) {
        this(fName, parseDate(DEFAULTDATE), sets, bannedCards, null, false, null, null, 0, FormatType.CUSTOM, FormatSubType.CUSTOM);
    }

    public static final GameFormat NoFormat = new GameFormat("(none)", parseDate(DEFAULTDATE), null, null, null, false
            , null, null, Integer.MAX_VALUE, FormatType.CUSTOM, FormatSubType.CUSTOM);

    public GameFormat(final String fName, final Date effectiveDate, final Iterable<String> sets, final List<String> bannedCards,
                      final List<String> restrictedCards, Boolean restrictedLegendary, final List<String> additionalCards,
                      final List<CardRarity> rarities, int compareIdx, FormatType formatType, FormatSubType formatSubType) {
        this.index = compareIdx;
        this.formatType = formatType;
        this.formatSubType = formatSubType;
        this.name = fName;
        this.effectiveDate = effectiveDate;

        if (sets != null) {
            StaticData data = StaticData.instance();
            Set<String> parsedSets = new HashSet<>();
            for (String set : sets) {
                if (data.getCardEdition(set) == null) {
                    System.out.println("Set " + set + " in format " + fName + " does not match any valid editions!");
                    continue;
                }
                parsedSets.add(set);
            }
            allowedSetCodes = Lists.newArrayList(parsedSets);
        } else {
            allowedSetCodes = new ArrayList<>();
        }

        bannedCardNames = bannedCards == null ? new ArrayList<>() : Lists.newArrayList(bannedCards);
        restrictedCardNames = restrictedCards == null ? new ArrayList<>() : Lists.newArrayList(restrictedCards);
        allowedRarities = rarities == null ? new ArrayList<>() : rarities;
        this.restrictedLegendary = restrictedLegendary;
        additionalCardNames = additionalCards == null ? new ArrayList<>() : Lists.newArrayList(additionalCards);

        this.allowedSetCodes_ro = Collections.unmodifiableList(allowedSetCodes);
        this.bannedCardNames_ro = Collections.unmodifiableList(bannedCardNames);
        this.restrictedCardNames_ro = Collections.unmodifiableList(restrictedCardNames);
        this.additionalCardNames_ro = Collections.unmodifiableList(additionalCardNames);

        this.filterRules = this.buildFilterRules();
        this.filterPrinted = this.buildFilterPrinted();
    }

    protected Predicate<PaperCard> buildFilter(boolean printed) {
        Predicate<PaperCard> p = Predicates.not(IPaperCard.Predicates.names(this.getBannedCardNames()));

        if (FormatSubType.ARENA.equals(this.getFormatSubType())) {
            p = Predicates.and(p, Predicates.not(IPaperCard.Predicates.Presets.IS_UNREBALANCED));
        } else {
            p = Predicates.and(p, Predicates.not(IPaperCard.Predicates.Presets.IS_REBALANCED));
        }

        if (!this.getAllowedSetCodes().isEmpty()) {
            p = Predicates.and(p, printed ?
                    IPaperCard.Predicates.printedInSets(this.getAllowedSetCodes(), printed) :
                    StaticData.instance().getCommonCards().wasPrintedInSets(this.getAllowedSetCodes()));
        }
        if (!this.getAllowedRarities().isEmpty()) {
            List<Predicate<? super PaperCard>> crp = Lists.newArrayList();
            for (CardRarity cr : this.getAllowedRarities()) {
                crp.add(StaticData.instance().getCommonCards().wasPrintedAtRarity(cr));
            }
            p = Predicates.and(p, Predicates.or(crp));
        }
        if (!this.getAdditionalCards().isEmpty()) {
            p = Predicates.or(p, IPaperCard.Predicates.names(this.getAdditionalCards()));
        }
        return p;
    }

    private Predicate<PaperCard> buildFilterPrinted() {
        return buildFilter(true);
    }

    private Predicate<PaperCard> buildFilterRules() {
        return buildFilter(false);
    }

    public String getName() {
        return this.name;
    }

    private static Date parseDate(String date) {
        if (date.length() <= 7)
            date = date + "-01";
        try {
            return formatter.parse(date);
        } catch (ParseException e) {
            return new Date();
        }
    }

    public Date getEffectiveDate() {
        return effectiveDate;
    }

    public FormatType getFormatType() {
        return this.formatType;
    }

    public FormatSubType getFormatSubType() {
        return this.formatSubType;
    }

    public List<String> getAllowedSetCodes() {
        return this.allowedSetCodes_ro;
    }

    public List<String> getBannedCardNames() {
        return this.bannedCardNames_ro;
    }

    public List<String> getRestrictedCards() {
        return restrictedCardNames_ro;
    }

    public Boolean isRestrictedLegendary() {
        return restrictedLegendary;
    }

    public List<String> getAdditionalCards() {
        return additionalCardNames_ro;
    }

    public List<CardRarity> getAllowedRarities() {
        return allowedRarities;
    }

    public List<PaperCard> getAllCards() {
        List<PaperCard> cards = new ArrayList<>();
        CardDb commonCards = StaticData.instance().getCommonCards();
        for (String setCode : allowedSetCodes_ro) {
            CardEdition edition = StaticData.instance().getEditions().get(setCode);
            if (edition != null) {
                for (CardInSet card : edition.getAllCardsInSet()) {
                    if (!bannedCardNames_ro.contains(card.name)) {
                        PaperCard pc = commonCards.getCard(card.name, setCode, card.collectorNumber);
                        if (pc != null) {
                            cards.add(pc);
                        }
                    }
                }
            }
        }
        return cards;
    }

    public Predicate<PaperCard> getFilterRules() {
        return this.filterRules;
    }

    public Predicate<PaperCard> getFilterPrinted() {
        return this.filterPrinted;
    }

    public boolean isSetLegal(final String setCode) {
        return this.getAllowedSetCodes().isEmpty() || this.getAllowedSetCodes().contains(setCode);
    }

    private boolean isPoolLegal(final CardPool allCards) {
        return getPoolLegalityProblem(allCards) == null;
    }

    public boolean isDeckLegal(final Deck deck) {
        return isPoolLegal(deck.getAllCardsInASinglePool());
    }

    private String getPoolLegalityProblem(final CardPool allCards) {
        // Check filter rules
        {
            final List<PaperCard> erroneousCI = new ArrayList<>();
            for (Entry<PaperCard, Integer> poolEntry : allCards) {
                if (!getFilterRules().apply(poolEntry.getKey())) {
                    erroneousCI.add(poolEntry.getKey());
                }
            }
            if (erroneousCI.size() > 0) {
                final StringBuilder sb = new StringBuilder("contains the following illegal cards:\n");
                for (final PaperCard cp : erroneousCI) {
                    sb.append("\n").append(cp.getName());
                }
                return sb.toString();
            }
        }
        // Check number of restricted and legendary-restricted cards
        if (!getRestrictedCards().isEmpty() || isRestrictedLegendary()) {
            final List<PaperCard> erroneousRestricted = new ArrayList<>();
            for (Entry<PaperCard, Integer> poolEntry : allCards) {
                boolean isRestricted = getRestrictedCards().contains(poolEntry.getKey().getName());
                boolean isLegendaryNonPlaneswalker = poolEntry.getKey().getRules().getType().isLegendary()
                        && !poolEntry.getKey().getRules().getType().isPlaneswalker() && isRestrictedLegendary();
                if (poolEntry.getValue() > 1 && (isRestricted || isLegendaryNonPlaneswalker)) {
                    erroneousRestricted.add(poolEntry.getKey());
                }
            }
            if (erroneousRestricted.size() > 0) {
                final StringBuilder sb = new StringBuilder("contains more than one copy of the following restricted cards:\n");
                for (final PaperCard cp : erroneousRestricted) {
                    sb.append("\n").append(cp.getName());
                }
                return sb.toString();
            }
        }
        return null;
    }

    /**
     * Check the conformance of a deck in this GameFormat.
     * Will check each card's set legality, and ensure no banned and max one of each restricted card exists.
     *
     * @param deck The deck to analyse
     * @return An error string describing the errors and associated cards, or null if no errors
     */
    public String getDeckConformanceProblem(final Deck deck) {
        if (deck == null) {
            return "is not selected";
        }
        return getPoolLegalityProblem(deck.getAllCardsInASinglePool());
    }

    @Override
    public String toString() {
        if ((this.formatType != FormatType.ARCHIVED) || (this.effectiveDate.equals(parseDate(DEFAULTDATE))))
            return this.name;
        String effectiveDateRepr = GameFormat.formatter.format(this.effectiveDate);
        if (this.name.contains(effectiveDateRepr))
            return this.name;
        return String.format("%s (%s)", this.name, effectiveDateRepr);
    }

    public static final Function<GameFormat, String> FN_GET_NAME = new Function<GameFormat, String>() {
        @Override
        public String apply(GameFormat arg1) {
            return arg1.getName();
        }
    };

    @Override
    public int compareTo(GameFormat other) {
        if (null == other) {
            return 1;
        }

        if (other.formatType != formatType)
            return formatType.compareTo(other.formatType);

        if (other.formatSubType != formatSubType)
            return formatSubType.compareTo(other.formatSubType);

        if (formatType.equals(FormatType.ARCHIVED)) {
            if (!this.effectiveDate.equals(parseDate(DEFAULTDATE)) && !other.effectiveDate.equals(parseDate(DEFAULTDATE))) {
                int compareDates = this.effectiveDate.compareTo(other.effectiveDate);
                if (compareDates != 0)
                    return compareDates;
            }
            if (this.index != other.index)
                // index are set in reverse order in files (latest formats have lower index).
                // Therefore: "other - this" -> "latest last"; "this - other" -> "latest first".
                return (other.index - this.index);
        }
        // falling back to name comparison as fallback for all cases!
        return name.compareTo(other.name);
    }

    public int getIndex() {
        return index;
    }

    public static class Reader extends StorageReaderRecursiveFolderWithUserFolder<GameFormat> {
        List<GameFormat> naturallyOrdered = new ArrayList<>();
        boolean includeArchived;
        private List<String> coreFormats = new ArrayList<>();

        {
            coreFormats.add("Standard.txt");
            coreFormats.add("Pioneer.txt");
            coreFormats.add("Historic.txt");
            coreFormats.add("Modern.txt");
            coreFormats.add("Legacy.txt");
            coreFormats.add("Vintage.txt");
            coreFormats.add("Commander.txt");
            coreFormats.add("Extended.txt");
            coreFormats.add("Brawl.txt");
            coreFormats.add("Oathbreaker.txt");
            coreFormats.add("Premodern.txt");
            coreFormats.add("Pauper.txt");
        }

        public Reader(File forgeFormats, File customFormats, boolean includeArchived) {
            super(forgeFormats, customFormats, GameFormat.FN_GET_NAME);
            this.includeArchived = includeArchived;
        }

        @Override
        protected GameFormat read(File file) {
            if (!includeArchived && !coreFormats.contains(file.getName())) {
                return null;
            }
            final Map<String, List<String>> contents = FileSection.parseSections(FileUtil.readFile(file));
            List<String> sets = null; // default: all sets allowed
            List<String> bannedCards = null; // default: nothing banned
            List<String> restrictedCards = null; // default: nothing restricted
            Boolean restrictedLegendary = false;
            List<String> additionalCards = null; // default: nothing additional
            List<CardRarity> rarities = null;
            List<String> formatStrings = contents.get("format");
            if (formatStrings == null) {
                return null;
            }
            FileSection section = FileSection.parse(formatStrings, FileSection.COLON_KV_SEPARATOR);
            String title = section.get("name");
            FormatType formatType;
            try {
                formatType = FormatType.valueOf(section.get("type").toUpperCase());
            } catch (Exception e) {
                if ("HISTORIC".equals(section.get("type").toUpperCase())) {
                    System.out.println("Historic is no longer used as a format Type. Please update " + file.getAbsolutePath() + " to use 'Archived' instead");
                    formatType = FormatType.ARCHIVED;
                } else {
                    formatType = FormatType.CUSTOM;
                }
            }
            FormatSubType formatsubType;
            try {
                formatsubType = FormatSubType.valueOf(section.get("subtype").toUpperCase());
            } catch (Exception e) {
                formatsubType = FormatSubType.CUSTOM;
            }
            Integer idx = section.getInt("order");
            String dateStr = section.get("effective");
            if (dateStr == null) {
                dateStr = DEFAULTDATE;
            }
            Date date = parseDate(dateStr);
            String strSets = section.get("sets");
            if (null != strSets) {
                sets = Arrays.asList(strSets.split(", "));
            }
            String strCars = section.get("banned");
            if (strCars != null) {
                bannedCards = Arrays.asList(strCars.split("; "));
            }

            strCars = section.get("restricted");
            if (strCars != null) {
                restrictedCards = Arrays.asList(strCars.split("; "));
            }

            Boolean strRestrictedLegendary = section.getBoolean("restrictedlegendary");
            if (strRestrictedLegendary != null) {
                restrictedLegendary = strRestrictedLegendary;
            }

            strCars = section.get("additional");
            if (strCars != null) {
                additionalCards = Arrays.asList(strCars.split("; "));
            }

            strCars = section.get("rarities");
            if (strCars != null) {
                CardRarity cr;
                rarities = Lists.newArrayList();
                for (String s : strCars.split(", ")) {
                    cr = CardRarity.smartValueOf(s);
                    if (!cr.name().equals("Unknown")) {
                        rarities.add(cr);
                    }
                }
            }

            GameFormat result = new GameFormat(title, date, sets, bannedCards, restrictedCards, restrictedLegendary, additionalCards, rarities, idx, formatType, formatsubType);
            naturallyOrdered.add(result);
            return result;
        }

        @Override
        protected FilenameFilter getFileFilter() {
            return TXT_FILE_FILTER;
        }

        public static final FilenameFilter TXT_FILE_FILTER = new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return name.endsWith(".txt") || dir.isDirectory();
            }
        };
    }

    public static class Collection extends StorageBase<GameFormat> {
        private List<GameFormat> naturallyOrdered;
        private List<GameFormat> reverseDateOrdered;

        public Collection(GameFormat.Reader reader) {
            super("Format collections", reader);
            naturallyOrdered = reader.naturallyOrdered;
            reverseDateOrdered = new ArrayList<>(naturallyOrdered);
            Collections.sort(naturallyOrdered);
            //Why this refactor doesnt work on some android phones? -> reverseDateOrdered.sort(new InverseDateComparator());
            Collections.sort(reverseDateOrdered, new InverseDateComparator());
        }

        public Iterable<GameFormat> getOrderedList() {
            return naturallyOrdered;
        }

        public Iterable<GameFormat> getReverseDateOrderedList() {
            return reverseDateOrdered;
        }

        public Iterable<GameFormat> getSanctionedList() {
            List<GameFormat> coreList = new ArrayList<>();
            for (GameFormat format : naturallyOrdered) {
                if (format.getFormatType().equals(FormatType.SANCTIONED)) {
                    coreList.add(format);
                }
            }
            return coreList;
        }

        private List<GameFormat> filterList;
        public Iterable<GameFormat> getFilterList() {
            if (filterList == null) {
                filterList = new ArrayList<>();
                for (GameFormat format : naturallyOrdered) {
                    if (format.getFormatType() != FormatType.ARCHIVED &&
                            format.getFormatType() != FormatType.DIGITAL) {
                        filterList.add(format);
                    }
                }
            }
            return filterList;
        }

        public Iterable<GameFormat> getFilterListWithAllowedSets() {
            List<GameFormat> formatsWithLimitedSets = new ArrayList<>();
            for (GameFormat format : this.getFilterList()) {
                if (format.getAllowedSetCodes().size() > 0)
                    formatsWithLimitedSets.add(format);
            }
            return formatsWithLimitedSets;
        }

        // =====================
        // === ARACHIVED FORMATS
        // =====================

        // storing archived list as it will be queried multiple times
        // to gather filters groups
        private List<GameFormat> archivedFormatsList;

        public Iterable<GameFormat> getArchivedList() {
            if (archivedFormatsList == null) {
                archivedFormatsList = new ArrayList<>();
                for (GameFormat format : naturallyOrdered) {
                    if (format.getFormatType().equals(FormatType.ARCHIVED)) {
                        archivedFormatsList.add(format);
                    }
                }
            }
            return archivedFormatsList;

        }

        public List<GameFormat> getArchivedList(FormatSubType subtype) {
            List<GameFormat> formatSubtypes = new ArrayList<>();
            for (GameFormat format : this.getArchivedList()) {
                if (format.getFormatSubType() == subtype)
                    formatSubtypes.add(format);
            }
            return formatSubtypes;
        }

        final private Set<GameFormat.FormatSubType> archivedSubtypeCategories = new LinkedHashSet<>(
                Arrays.asList(
                        GameFormat.FormatSubType.STANDARD,
                        GameFormat.FormatSubType.MODERN,
                        GameFormat.FormatSubType.LEGACY,
                        GameFormat.FormatSubType.VINTAGE,
                        GameFormat.FormatSubType.BLOCK,
                        GameFormat.FormatSubType.EXTENDED,
                        GameFormat.FormatSubType.PIONEER,
                        GameFormat.FormatSubType.ARENA
                )
        );

        private Map<String, List<GameFormat>> archivedFormatGroups;

        /**
         * This method returns Archived formats organised per-Category, that is:
         * for each format-subtype, formats will be then grouped by name prefix, following
         * the natural (chronological) order of archived formats.
         * Since formats are initialised at loading-time, format-groupings will be stored
         * and returned for future calls.
         * This method will be used to organise formats in "Choose Formats" filters (mobile/desktop).
         *
         * @return a map associating each category (key) to the list of corresponding formats.
         */
        public Map<String, List<GameFormat>> getArchivedListPerCategory() {

            if (archivedFormatGroups == null) {
                archivedFormatGroups = new LinkedHashMap<>(); // crucial to preserve natural ordering

                for (GameFormat.FormatSubType formatSubType : this.archivedSubtypeCategories) {
                    List<GameFormat> subtypeFormats = this.getArchivedList(formatSubType);
                    for (GameFormat format : subtypeFormats) {
                        String key = getFormatSubTypeKey(formatSubType, format);
                        archivedFormatGroups.putIfAbsent(key, new ArrayList<>());
                        List<GameFormat> formatsPerKey = archivedFormatGroups.get(key);
                        formatsPerKey.add(format);
                    }
                }
            }
            return archivedFormatGroups;
        }

        private final String CATEGORY_SEPARATOR = "-";
        private String getFormatSubTypeKey(FormatSubType formatSubType, GameFormat format) {
            String key = String.format("%s%s%s",
                    format.getFormatType().name(),
                    CATEGORY_SEPARATOR,
                    format.getFormatSubType().name());
            if ((formatSubType != FormatSubType.BLOCK) || !(format.getName().endsWith("Block"))) {
                String[] namePrefixes = StringUtils.splitByWholeSeparator(format.getName(), " (");
                // getting common prefix name as per last-level grouping
                key += String.format(": %s", namePrefixes[0]);
            }
            return key;
        }

        public Iterable<GameFormat> getMainBlockFormatsList() {
            List<GameFormat> blockFormats = new ArrayList<>();
            for (GameFormat format : this.getArchivedList()) {
                if (format.getFormatSubType() != GameFormat.FormatSubType.BLOCK)
                    continue;
                if (!format.getName().endsWith("Block"))
                    continue;
                blockFormats.add(format);
            }
            Collections.sort(blockFormats);
            return blockFormats;
        }

        // ================

        public Iterable<GameFormat> getCasualList() {
            List<GameFormat> casualList = new ArrayList<>();
            for (GameFormat format : naturallyOrdered) {
                if (format.getFormatType().equals(FormatType.CASUAL)) {
                    casualList.add(format);
                }
            }
            return casualList;
        }

        public Iterable<GameFormat> getFormatTypeList(FormatType formatType) {
            List<GameFormat> filterList = new ArrayList<>();
            for (GameFormat format : naturallyOrdered) {
                if (format.getFormatType() == formatType) {
                    filterList.add(format);
                }
            }
            return filterList;
        }

        public boolean allFormatsEnabled() {
            // Just trying to get a random (block) format that is surely archived
            // in order to figure out whether archived formats have been loaded
            return this.getFormat("Odyssey Block") != null;
        }

        public GameFormat getStandard() {
            return this.map.get("Standard");
        }

        public GameFormat getExtended() {
            return this.map.get("Extended");
        }

        public GameFormat getPioneer() {
            return this.map.get("Pioneer");
        }

        public GameFormat getHistoric() {
            return this.map.get("Historic");
        }

        public GameFormat getModern() {
            return this.map.get("Modern");
        }

        public GameFormat getVintage() {
            return this.map.get("Vintage");
        }

        public GameFormat getPremodern() { return this.map.get("Premodern"); }

        public GameFormat getPauper() { return this.map.get("Pauper"); }

        public GameFormat getFormat(String format) {
            return this.map.get(format);
        }

        public GameFormat getFormatOfDeck(Deck deck) {
            for (GameFormat gf : reverseDateOrdered) {
                if (gf.isDeckLegal(deck))
                    return gf;
            }
            return NoFormat;
        }

        public Set<GameFormat> getAllFormatsOfCard(PaperCard card) {
            Set<GameFormat> result = new HashSet<>();
            for (GameFormat gf : naturallyOrdered) {
                if (gf.getFilterRules().apply(card)) {
                    result.add(gf);
                }
            }
            if (result.isEmpty()) {
                result.add(NoFormat);
            }
            return result;
        }

        public Set<GameFormat> getAllFormatsOfDeck(Deck deck) {
            return getAllFormatsOfDeck(deck, false);
        }

        public Set<GameFormat> getAllFormatsOfDeck(Deck deck, Boolean exhaustive) {
            SortedSet<GameFormat> result = new TreeSet<>();
            Set<FormatSubType> coveredTypes = new HashSet<>();
            CardPool allCards = deck.getAllCardsInASinglePool();
            for (GameFormat gf : reverseDateOrdered) {
                if (gf.getFormatType().equals(FormatType.DIGITAL) && !exhaustive) {
                    //exclude Digital formats from lists for now
                    continue;
                }
                if (gf.getFormatSubType().equals(FormatSubType.COMMANDER)) {
                    //exclude Commander format as other deck checks are not performed here
                    continue;
                }
                if (gf.getFormatType().equals(FormatType.ARCHIVED) && coveredTypes.contains(gf.getFormatSubType())
                        && !exhaustive) {
                    //exclude duplicate formats - only keep first of e.g. Standard archived
                    continue;
                }
                if (gf.isPoolLegal(allCards)) {
                    result.add(gf);
                    coveredTypes.add(gf.getFormatSubType());
                }
            }
            if (result.isEmpty()) {
                result.add(NoFormat);
            }
            return result;
        }

        @Override
        public void add(GameFormat item) {
            naturallyOrdered.add(item);
        }
    }

    public static class InverseDateComparator implements Comparator<GameFormat> {
        public int compare(GameFormat gf1, GameFormat gf2) {
            if ((null == gf1) || (null == gf2)) {
                return 1;
            }
            if (gf2.formatType != gf1.formatType) {
                return gf1.formatType.compareTo(gf2.formatType);
            }
            if (gf2.formatSubType != gf1.formatSubType) {
                return gf1.formatSubType.compareTo(gf2.formatSubType);
            }
            if (gf1.formatType.equals(FormatType.ARCHIVED)) {
                //for matching dates or default dates default to index / name sorting
                if (!gf1.effectiveDate.equals(parseDate(DEFAULTDATE))
                        && !gf2.effectiveDate.equals(parseDate(DEFAULTDATE))
                        && gf1.effectiveDate != gf2.effectiveDate)
                    return gf1.effectiveDate.compareTo(gf2.effectiveDate);

                if (gf1.index != gf2.index)
                    // index are set in reverse order in files (latest formats have lower index).
                    // See also: GameFormat.compareTo
                    return (gf1.index - gf2.index);
            }
            return gf1.name.compareTo(gf2.name);
        }
    }

    public final Predicate<CardEdition> editionLegalPredicate = new Predicate<CardEdition>() {
        @Override
        public boolean apply(final CardEdition subject) {
            return GameFormat.this.isSetLegal(subject.getCode());
        }
    };
}
