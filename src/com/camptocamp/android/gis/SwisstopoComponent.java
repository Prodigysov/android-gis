package com.camptocamp.android.gis;

import com.nutiteq.BasicMapComponent;
import com.nutiteq.components.WgsPoint;

public class SwisstopoComponent extends BasicMapComponent {

    // private static final String TAG = Map.D + "SwisstopoComponent";
    private static final String KEY = "182be0c5cdcd5072bb1864cdee4d3d6e4c593f89365962.70956542";

    public SwisstopoComponent(WgsPoint middlePoint, int zoom) {
        super(KEY, Map.VDR, Map.APP, 1, 1, middlePoint, zoom);
    }

    @Override
    public void zoomIn() {
        if (middlePoint.getZoom() == displayedMap.getMaxZoom()) {
            return;
        }
        int z1 = middlePoint.getZoom();
        cleanMapBuffer();
        middlePoint = displayedMap.zoom(middlePoint, 1);
        tileMapBounds = displayedMap.getTileMapBounds(middlePoint.getZoom());

        // Zoom buffer according to map resolution
        double ratio = SwisstopoMap.resolutions.get(z1)
                / SwisstopoMap.resolutions.get(middlePoint.getZoom());

        createZoomBufferAndUpdateScreen(Math.log(ratio) / Math.log(2), true, false);

    }

    @Override
    public void zoomOut() {
        if (middlePoint.getZoom() == displayedMap.getMinZoom()) {
            return;
        }
        int z1 = middlePoint.getZoom();
        cleanMapBuffer();
        middlePoint = displayedMap.zoom(middlePoint, -1);
        tileMapBounds = displayedMap.getTileMapBounds(middlePoint.getZoom());

        // Zoom buffer according to map resolution
        double ratio = SwisstopoMap.resolutions.get(middlePoint.getZoom())
                / SwisstopoMap.resolutions.get(z1);

        createZoomBufferAndUpdateScreen(Math.log(ratio) / Math.log(2), true, true);
    }
}
