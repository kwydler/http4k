package org.http4k.connect.slack

import dev.forkhandles.result4k.Result
import org.http4k.connect.Action
import org.http4k.connect.RemoteFailure

interface SlackAction<R> : Action<Result<R, RemoteFailure>>

