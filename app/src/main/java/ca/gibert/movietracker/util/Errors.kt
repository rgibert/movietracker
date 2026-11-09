package ca.gibert.movietracker.util

import retrofit2.HttpException
import java.io.IOException

fun friendlyError(error: Throwable): String = when (error) {
    is IOException -> "Couldn't reach Trakt. Check your connection."
    is HttpException -> when (error.code()) {
        401 -> "Your Trakt session expired. Please reconnect."
        403 -> "Trakt denied that request."
        404 -> "That item wasn't found on Trakt."
        409 -> "Trakt already has that."
        422 -> "Trakt didn't accept that. Reviews need at least five words."
        429 -> "Trakt is rate limiting requests. Try again in a minute."
        in 500..599 -> "Trakt is having trouble right now. Try again shortly."
        else -> "Trakt returned HTTP ${error.code()}."
    }

    else -> error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
}
