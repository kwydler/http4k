package org.http4k.server

import org.eclipse.jetty.util.BufferUtil
import org.eclipse.jetty.util.ByteArrayOutputStream2
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.websocket.core.CloseStatus
import org.eclipse.jetty.websocket.core.CoreSession
import org.eclipse.jetty.websocket.core.Frame
import org.eclipse.jetty.websocket.core.FrameHandler
import org.eclipse.jetty.websocket.core.OpCode.BINARY
import org.eclipse.jetty.websocket.core.OpCode.CONTINUATION
import org.eclipse.jetty.websocket.core.OpCode.TEXT
import org.http4k.core.MemoryBody
import org.http4k.websocket.PushPullAdaptingWebSocket
import org.http4k.websocket.WsConsumer
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsStatus

class Http4kWebSocketFrameHandler(private val consumer: WsConsumer) : FrameHandler {
    private var websocket: PushPullAdaptingWebSocket? = null
    private val buffer = ByteArrayOutputStream2()
    private var messageMode =  WsMessage.Mode.Text

    override fun onFrame(frame: Frame, callback: Callback) {
        try {
            when (frame.opCode) {
                TEXT -> messageMode = WsMessage.Mode.Text
                BINARY -> messageMode = WsMessage.Mode.Binary
            }

            when(frame.opCode) {
                TEXT, BINARY, CONTINUATION -> {
                    buffer.write(BufferUtil.toArray(frame.payload))

                    if (frame.isFin) {
                        websocket?.triggerMessage(WsMessage(MemoryBody(buffer.toByteArray()), messageMode))
                        buffer.reset()
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
        websocket = object : PushPullAdaptingWebSocket() {
            override fun send(message: WsMessage) {

                session.sendFrame(Frame(
                    when(message.mode) {
                        WsMessage.Mode.Binary -> BINARY
                        WsMessage.Mode.Text -> TEXT
                    },
                    message.body.payload), object : Callback {
                    override fun succeeded() = session.flush(object : Callback {})
                }, false)
            }

            override fun close(status: WsStatus) {
                session.close(status.code, status.description, object : Callback {
                    override fun succeeded() = session.flush(object : Callback {})
                })
            }
        }.apply(consumer)
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
