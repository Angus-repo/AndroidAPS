package app.aaps.plugins.sync.googlehealth.events

import app.aaps.core.interfaces.rx.events.Event
import java.text.SimpleDateFormat
import java.util.Locale

class EventGoogleHealthNewLog(val action: String, val logText: String?) : Event() {

    var date = System.currentTimeMillis()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun toPreparedHtml(): StringBuilder {
        val sb = StringBuilder()
        sb.append(timeFormat.format(date))
        sb.append(" <b>")
        sb.append(action)
        sb.append("</b> ")
        sb.append(logText)
        sb.append("<br>")
        return sb
    }
}
