package forge.itemmanager.filters;

import java.util.*;

import com.badlogic.gdx.utils.Align;

import forge.Forge;
import forge.Graphics;
import forge.assets.FSkinColor;
import forge.assets.FSkinFont;
import forge.game.GameFormat;
import forge.model.FModel;
import forge.screens.FScreen;
import forge.screens.settings.SettingsScreen;
import forge.toolbox.FGroupList;
import forge.toolbox.FList;
import forge.util.Callback;
import forge.util.Utils;

/**
 * Created by maustin on 16/04/2018.
 */
public class ArchivedFormatSelect extends FScreen {

    private GameFormat selectedFormat;
    private final FGroupList<GameFormat> lstFormats = add(new FGroupList<>());

    private Runnable onCloseCallBack;

    public ArchivedFormatSelect() {
        super(Forge.getLocalizer().getMessage("lblChooseFormat"));
        // Initialise Groups in the Menu list
        Map<String, Integer> categoryGroupIndex = new HashMap<>();
        int groupIndex = 0;
        for (GameFormat.FormatType group:GameFormat.FormatType.values()) {
            if (group != GameFormat.FormatType.ARCHIVED) {
                lstFormats.addGroup(group.name());
                categoryGroupIndex.put(group.name(), groupIndex);
                groupIndex += 1;
            }

        }

        boolean loadArchiveFormats = FModel.getFormats().allFormatsEnabled();
        Map<String, List<GameFormat>> archivedPerGroup = null;
        if (loadArchiveFormats) {
            archivedPerGroup = FModel.getFormats().getArchivedListPerCategory();
            for (String categoryName: archivedPerGroup.keySet()){
                lstFormats.addGroup(categoryName);
                categoryGroupIndex.put(categoryName, groupIndex);
                groupIndex += 1;
            }
        }


        for (GameFormat format: FModel.getFormats().getFilterList())
            lstFormats.addItem(format, categoryGroupIndex.get(format.getFormatType().name()));
        for (GameFormat format: FModel.getFormats().getFormatTypeList(GameFormat.FormatType.DIGITAL))
            lstFormats.addItem(format, categoryGroupIndex.get(format.getFormatType().name()));
        for (GameFormat format: FModel.getFormats().getFormatTypeList(GameFormat.FormatType.CUSTOM))
            lstFormats.addItem(format, categoryGroupIndex.get(format.getFormatType().name()));

        if (loadArchiveFormats){
            for (String categoryName: archivedPerGroup.keySet()){
                int index = categoryGroupIndex.get(categoryName);
                for (GameFormat format : archivedPerGroup.get(categoryName)){
                    lstFormats.addItem(format, index);
                }
            }
        }
        lstFormats.setListItemRenderer(new FormatRenderer());
        if (loadArchiveFormats)
            lstFormats.collapseAll();
    }

    public GameFormat getSelectedFormat() {
        return selectedFormat;
    }

    public void setOnCloseCallBack(Runnable onCloseCallBack) {
        this.onCloseCallBack = onCloseCallBack;
    }

    @Override
    public void onClose(Callback<Boolean> canCloseCallback) {
        if (selectedFormat != null) {
            if (onCloseCallBack != null) {
                onCloseCallBack.run();
            }
        }
        super.onClose(canCloseCallback);
    }

    @Override
    protected void doLayout(float startY, float width, float height) {
        lstFormats.setBounds(0, startY, width, height - startY);
    }

    private class FormatRenderer extends FList.ListItemRenderer<GameFormat>{
        @Override
        public float getItemHeight() {
            return Utils.AVG_FINGER_HEIGHT;
        }

        @Override
        public boolean tap(Integer index, GameFormat value, float x, float y, int count) {
            selectedFormat=value;
            Forge.back();
            return true;
        }

        @Override
        public void drawValue(Graphics g, Integer index, GameFormat value, FSkinFont font, FSkinColor foreColor, FSkinColor backColor, boolean pressed, float x, float y, float w, float h) {
            float offset = SettingsScreen.getInsets(w) - FList.PADDING; //increase padding for settings items
            x += offset;
            y += offset;
            w -= 2 * offset;
            h -= 2 * offset;

            float textHeight = h;
            h *= 0.66f;

            g.drawText(value.toString(), font, foreColor, x, y, w - h - FList.PADDING, textHeight, false, Align.left, true);

            x += w - h;
            y += (textHeight - h) / 2;
        }
    }
}
