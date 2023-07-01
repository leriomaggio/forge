package forge.deck;

import forge.deck.io.DeckSerializer;
import forge.deck.io.DeckStorage;
import forge.game.GameType;
import forge.gui.GuiBase;
import forge.gui.download.GuiDownloadZipService;
import forge.gui.util.SGuiChoose;
import forge.localinstance.properties.ForgeConstants;
import forge.util.FileUtil;
import forge.util.WaitCallback;
import forge.util.storage.StorageBase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class NetDeckArchiveVintage extends StorageBase<Deck> {
    public static final String PREFIX = "NET_ARCHIVE_VINTAGE_DECK";
    private static Map<String, NetDeckArchiveVintage> constructed, commander, brawl;

    private static Map<String, NetDeckArchiveVintage> loadCategories(String filename) {
        Map<String, NetDeckArchiveVintage> categories = new TreeMap<>();
        if (FileUtil.doesFileExist(filename)) {
            List<String> lines = FileUtil.readFile(filename);
            for (String line : lines) {
                int idx = line.indexOf('|');
                if (idx != -1) {
                    String name = line.substring(0, idx).trim();
                    String url = line.substring(idx + 1).trim();
                    categories.put(name, new NetDeckArchiveVintage(name, url));
                }
            }
        }
        return categories;
    }

    public static NetDeckArchiveVintage selectAndLoad(GameType gameType) {
        return selectAndLoad(gameType, null);
    }
    public static NetDeckArchiveVintage selectAndLoad(GameType gameType, String name) {
        Map<String, NetDeckArchiveVintage> categories;
        switch (gameType) {
            case Constructed:
            case Gauntlet:
                if (constructed == null) {
                    constructed = loadCategories(ForgeConstants.NET_ARCHIVE_VINTAGE_DECKS_LIST_FILE);
                }
                categories = constructed;
                break;
            default:
                return null;
        }

        if (name != null) {
            NetDeckArchiveVintage category = categories.get(name);
            if (category != null && category.map.isEmpty()) {
                //if name passed in, try to load decks from current cached files
                File downloadDir = new File(category.getFullPath());
                if (downloadDir.exists()) {
                    for (File file : getAllFilesList(downloadDir, DeckStorage.DCK_FILE_FILTER)) {
                        Deck deck = DeckSerializer.fromFile(file);
                        if (deck != null) {
                            category.map.put(deck.getName(), deck);
                        }
                    }
                }
            }
            return category;
        }

        List<NetDeckArchiveVintage> category = new ArrayList<>(categories.values());
        Collections.reverse(category);

        final NetDeckArchiveVintage c = SGuiChoose.oneOrNone("Select a Net Deck Archive Vintage category", category);
        if (c == null) { return null; }

        if (c.map.isEmpty()) { //only download decks once per session
            WaitCallback<Boolean> callback = new WaitCallback<Boolean>() {
                @Override
                public void run() {
                    String downloadLoc = c.getFullPath();
                    GuiBase.getInterface().download(new GuiDownloadZipService(c.getName(), "decks", c.getUrl(), downloadLoc, downloadLoc, null) {
                        @Override
                        protected void copyInputStream(InputStream in, String outPath) throws IOException {
                            super.copyInputStream(in, outPath);

                            Deck deck = DeckSerializer.fromFile(new File(outPath));
                            if (deck != null) {
                                c.map.put(deck.getName(), deck);
                            }
                        }
                    }, this);
                }
            };
            if (!callback.invokeAndWait()) { return null; } //wait for download to finish
        }
        return c;
    }

    private final String url;

    private NetDeckArchiveVintage(String name0, String url0) {
        super(name0, ForgeConstants.DECK_NET_ARCHIVE_DIR + name0, new HashMap<>());
        url = url0;
    }


    public String getUrl() {
        return url;
    }

    public String getDeckType() {
        return "Net Archive Vintage Decks - " + name;
    }

    @Override
    public String toString() {
        return name;
    }
}
