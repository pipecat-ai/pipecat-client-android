package ai.pipecat.client.types

import android.util.Base64

/**
 * A file sent with its content.
 *
 * @param data The file's content.
 * @param format The file's MIME type, such as `image/png` or `application/pdf`.
 * @param name The file's name.
 */
fun RTVIFile(data: ByteArray, format: String, name: String? = null) = RTVIFile(
    format = format,
    source = FileBytes(bytes = Base64.encodeToString(data, Base64.NO_WRAP)),
    name = name
)

/**
 * A file the bot fetches from a URL.
 *
 * @param url The file's URL: an `http(s)` URL, a cloud-storage URI such as `s3://`, or what the
 * bot's upload endpoint returned.
 * @param format The file's MIME type, such as `image/png` or `application/pdf`.
 * @param name The file's name.
 */
fun RTVIFile(url: String, format: String, name: String? = null) = RTVIFile(
    format = format,
    source = FileUrl(url = url),
    name = name
)
