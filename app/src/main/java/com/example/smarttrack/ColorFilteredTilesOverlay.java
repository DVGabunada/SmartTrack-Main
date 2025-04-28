package com.example.smarttrack;

import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;

/**
 * Custom Tile Overlay to apply a Gold & Maroon color filter to OpenStreetMap tiles.
 */
public class ColorFilteredTilesOverlay extends TilesOverlay {
    private final Paint paint = new Paint();

    public ColorFilteredTilesOverlay(MapTileProviderBase tileProvider) {
        super(tileProvider, null);

        // **🔥 Apply Gold & Maroon Filter**
        ColorMatrix colorMatrix = new ColorMatrix(new float[]{
                1.3f, 0.3f, 0.1f, 0, 30,  // Red → Gold
                0.1f, 1.2f, 0.2f, 0, 20,  // Green → Maroon tint
                0.1f, 0.2f, 1.0f, 0, 10,  // Blue remains blue (slightly desaturated)
                0, 0, 0, 1, 0              // Alpha remains unchanged
        });

        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (!shadow) {
            canvas.drawPaint(paint);
        }
        super.draw(canvas, mapView, shadow);
    }
}
