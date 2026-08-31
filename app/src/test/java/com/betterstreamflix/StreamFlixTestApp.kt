package com.betterstreamflix

import android.app.Application

/** Lightweight test application — avoids Conscrypt JNI and heavy StreamFlixApp init. */
class StreamFlixTestApp : Application()
