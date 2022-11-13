package org.http4k.server

import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.Utf8StringBuilder
import org.eclipse.jetty.websocket.core.CloseStatus
import org.eclipse.jetty.websocket.core.CoreSession
import org.eclipse.jetty.websocket.core.Frame
import org.eclipse.jetty.websocket.core.FrameHandler
import org.eclipse.jetty.websocket.core.OpCode.BINARY
import org.eclipse.jetty.websocket.core.OpCode.CONTINUATION
import org.eclipse.jetty.websocket.core.OpCode.TEXT
import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.StreamBody
import org.http4k.websocket.PushPullAdaptingWebSocket
import org.http4k.websocket.WsConsumer
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class Http4kWebSocketFrameHandler(private val wSocket: WsConsumer,
                                  private val upgradeRequest: Request) : FrameHandler {

    private var websocket: PushPullAdaptingWebSocket? = null
    private var textSink: MessageSink? = null
    private var binarySink: MessageSink? = null
    private var activeSink: MessageSink? = null

    override fun onFrame(frame: Frame, callback: Callback) {
        try {
            when (frame.opCode) {
                TEXT -> {
                    if (activeSink == null) {
                        activeSink = textSink
                    }
                }
                BINARY -> {
                    if (activeSink == null) {
                        activeSink = binarySink
                    }
                }
            }
            when (frame.opCode) {
                TEXT, BINARY, CONTINUATION -> {
                    activeSink?.accept(frame)

                    if (frame.isFin) {
                        activeSink = null
                    }
                }
            }
            callback.succeeded()
        } catch (e: Throwable) {
            websocket?.triggerError(e)
            callback.failed(e)
        }
    }

    override fun onOpen(session: CoreSession, callback: Callback) {
        val ws = object : PushPullAdaptingWebSocket(upgradeRequest) {
            override fun send(message: WsMessage) {
                session.sendFrame(Frame(
                    if (message.body is StreamBody) BINARY else TEXT,
                    message.body.payload), object : Callback {
                    override fun succeeded() = session.flush(object : Callback {})
                }, false)
            }

            override fun close(status: WsStatus) {
                session.close(status.code, status.description, object : Callback {
                    override fun succeeded() = session.flush(object : Callback {})
                })
            }
        }.apply(wSocket)

        websocket = ws
        textSink = TextMessageSink(ws)
        binarySink = BinaryMessageSink(ws)

        callback.succeeded()
    }

    override fun onError(cause: Throwable, callback: Callback) {
        websocket?.triggerError(cause)
        callback.succeeded()
    }

    override fun onClosed(closeStatus: CloseStatus, callback: Callback) {
        websocket?.triggerClose(WsStatus(closeStatus.code, closeStatus.reason ?: "<unknown>"))
    }
}

private interface MessageSink {

    fun accept(frame: Frame)
}

private class BinaryMessageSink(private val socket: PushPullAdaptingWebSocket) : MessageSink {

    private var out: ByteArrayOutputStream = ByteArrayOutputStream()

    override fun accept(frame: Frame) {
        try {
            if (frame.hasPayload()) {
                val bytes = ByteArray(frame.payload.remaining())
                frame.payload.get(bytes)
                out.write(bytes)
            }

            if (frame.isFin) {
                val stream = ByteArrayInputStream(out.toByteArray())
                socket.triggerMessage(WsMessage(Body(stream)))
            }
        } finally {
            if(frame.isFin) {
                out.reset()
            }
        }
    }
}

private class TextMessageSink(private val socket: PushPullAdaptingWebSocket) : MessageSink {

    private var out: Utf8StringBuilder = Utf8StringBuilder()

    override fun accept(frame: Frame) {
        try {
            if (frame.hasPayload()) {
                out.append(frame.payload)
            }

            if(frame.isFin) {
                val str = out.toString()
                socket.triggerMessage(WsMessage(Body(str)))
            }
        } finally {
            if(frame.isFin) {
                out.reset()
            }
        }
    }
}
