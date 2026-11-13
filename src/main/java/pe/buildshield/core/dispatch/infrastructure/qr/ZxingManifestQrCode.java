package pe.buildshield.core.dispatch.infrastructure.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;
import pe.buildshield.core.dispatch.application.ManifestQrCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** QR del manifiesto con ZXing: PNG de 256 px, corrección de errores media. */
@Component
class ZxingManifestQrCode implements ManifestQrCode {

    static final int SIZE = 256;

    @Override
    public byte[] png(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return png.toByteArray();
        } catch (WriterException ex) {
            throw new IllegalStateException("No se pudo generar el QR del manifiesto", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
