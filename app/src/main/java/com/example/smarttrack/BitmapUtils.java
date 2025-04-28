package com.example.smarttrack;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.camera.core.ImageProxy;
import java.nio.ByteBuffer;

public class BitmapUtils {
    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        @SuppressWarnings("UnsafeOptInUsageError")
        ImageProxy.PlaneProxy buffer = imageProxy.getPlanes()[0];
        ByteBuffer byteBuffer = buffer.getBuffer();
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
