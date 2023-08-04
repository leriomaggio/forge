package forge.screens.home.quest;

import forge.Singletons;

public abstract class FChooseDialog {

    protected int getMainDialogWidth() {
        int winWidth = Singletons.getView().getFrame().getSize().width;
        int[] sizeBoundaries = new int[] {800, 1024, 1280, 2048};
        return calculateRelativePanelDimension(winWidth, 90, sizeBoundaries);
    }

    // So far, not yet used, but left here just in case
    protected int getMainDialogHeight() {
        int winHeight = Singletons.getView().getFrame().getSize().height;
        int[] sizeBoundaries = new int[] {600, 720, 780, 1024};
        return calculateRelativePanelDimension(winHeight, 40, sizeBoundaries);
    }

    protected int calculateRelativePanelDimension(int winDim, int ratio, int[] sizeBoundaries){
        int relativeWinDimension = winDim * ratio / 100;
        if (winDim < sizeBoundaries[0])
            return relativeWinDimension;
        for (int i = 1; i < sizeBoundaries.length; i++){
            int left = sizeBoundaries[i-1];
            int right = sizeBoundaries[i];
            if (winDim <= left || winDim > right)
                continue;
            return Math.min(right*90/100, relativeWinDimension);
        }
        return sizeBoundaries[sizeBoundaries.length - 1] * 90 / 100;  // Max Size fixed
    }
}
