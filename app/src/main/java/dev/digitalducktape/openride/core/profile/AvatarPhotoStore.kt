package dev.digitalducktape.openride.core.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Owns the rider avatar photos on disk (feedback: "allow a photo to be taken using the built
 * in camera and then cropped to a profile circle"). Photos live in the app's private files
 * area; [dev.digitalducktape.openride.core.data.Profile.avatarPhotoPath] points at them.
 *
 * [importCapture] turns a raw camera capture into a stored avatar: EXIF-oriented,
 * center-square cropped (the square that the UI then clips to a circle), and downscaled to
 * [AVATAR_SIZE_PX] so years of avatars stay a few tens of KB each. Raw bytes round-trip via
 * [readBytes]/[saveBytes] so backups can carry the photo itself (not just an
 * install-specific path) across reinstalls.
 */
class AvatarPhotoStore(private val photosDir: File) {

    /**
     * Processes the camera's raw capture in [captureFile] into a stored avatar photo and
     * returns its absolute path, or `null` if the capture can't be decoded. The capture file
     * itself is deleted afterward — only the cropped avatar is kept.
     */
    fun importCapture(captureFile: File): String? {
        val bitmap = decodeOriented(captureFile) ?: return null
        try {
            val side = minOf(bitmap.width, bitmap.height)
            if (side <= 0) return null
            val square = Bitmap.createBitmap(
                bitmap,
                (bitmap.width - side) / 2,
                (bitmap.height - side) / 2,
                side,
                side,
            )
            val scaled = if (side > AVATAR_SIZE_PX) {
                Bitmap.createScaledBitmap(square, AVATAR_SIZE_PX, AVATAR_SIZE_PX, true)
            } else {
                square
            }
            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
            return saveBytes(bytes)
        } finally {
            captureFile.delete()
        }
    }

    /** Persists raw JPEG [bytes] as a new avatar photo file, returning its absolute path. */
    fun saveBytes(bytes: ByteArray): String {
        photosDir.mkdirs()
        val file = File.createTempFile("avatar_", ".jpg", photosDir)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /** The photo file's raw bytes, or `null` if [path] no longer points at a readable file. */
    fun readBytes(path: String): ByteArray? =
        runCatching { File(path).takeIf { it.isFile }?.readBytes() }.getOrNull()

    /** Deletes the photo file at [path] (e.g. when a rider replaces or removes their photo). */
    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    /** Decodes [file] downsampled near the target size, rotated per its EXIF orientation. */
    private fun decodeOriented(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(minOf(bounds.outWidth, bounds.outHeight))
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        val rotationDegrees = when (
            runCatching {
                ExifInterface(file).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotationDegrees == 0f) return decoded

        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    /** Largest power-of-two downsample that still keeps the short edge >= [AVATAR_SIZE_PX]. */
    private fun sampleSize(shortEdge: Int): Int {
        var sample = 1
        while (shortEdge / (sample * 2) >= AVATAR_SIZE_PX) sample *= 2
        return sample
    }

    companion object {
        /** Stored avatar edge in px — plenty for the largest circle (180dp on profile select). */
        const val AVATAR_SIZE_PX = 512
        private const val JPEG_QUALITY = 85
    }
}
