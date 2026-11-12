// TODO: Validate
package com.streamchanneler.app.data

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import javax.net.ssl.SSLException
import org.json.JSONException

// TODO: Validate
fun describeLoadError(loadError: Throwable): String =
    when (val underlyingLoadError = unwrappedLoadError(loadError)) {
        is ApiException if underlyingLoadError.isSignInRejected ->
            "your sign-in has expired"
        is ApiException ->
            "the server answered ${underlyingLoadError.statusCode}: ${underlyingLoadError.message}"
        is JSONException ->
            "the server sent data the app can't read (${underlyingLoadError.message})"
        is SocketTimeoutException ->
            "the server took too long to answer"
        is UnknownHostException, is ConnectException ->
            "can't reach ${StreamChannelerApi.API_ORIGIN.removePrefix("https://")}. Check the TV's internet connection"
        is SSLException ->
            "a secure connection to the server failed (${underlyingLoadError.message}). Check the TV's date and time"
        else -> underlyingLoadError.message ?: underlyingLoadError.javaClass.simpleName
    }

// TODO: Validate
fun isSignInRejectedError(loadError: Throwable): Boolean =
    (unwrappedLoadError(loadError) as? ApiException)?.isSignInRejected == true

// TODO: Validate
private fun unwrappedLoadError(loadError: Throwable): Throwable =
    if (loadError is ExecutionException) loadError.cause ?: loadError else loadError
