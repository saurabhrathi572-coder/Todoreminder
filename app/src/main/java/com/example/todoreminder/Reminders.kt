package com.example.todoreminder

import android.app.*
import android.content.*
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class Task(val id: Long, val title: String, val time: Long, val done: Boolean)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("tasks", Context.MODE_PRIVATE)
    fun load(c: Context): List<Task> {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Task(o.getLong("id"), o.getString("title"), o.getLong("time"), o.getBoolean("done"))
        }
    }
    fun save(c: Context, list: List<Task>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title)
                .put("time", it.time).put("done", it.done))
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }
}

object Scheduler {
    private fun pi(c: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            c, id.toInt(),
            Intent(c, ReminderReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(c: Context, id: Long, at: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, id))
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, id))
    }

    fun cancel(c: Context, id: Long) =
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(c, id))
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", -1)
        val task = Store.load(c).find { it.id == id } ?: return
        if (task.done) return

        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("todo", "Todo reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(
            id.toInt(),
            Notification.Builder(c, "todo")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Reminder: " + task.title)
                .setContentText("Not completed yet. Tap to open.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
        // Not completed? Remind again in 30 minutes.
        Scheduler.schedule(c, id, System.currentTimeMillis() + 30 * 60 * 1000)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store.load(c).filter { !it.done }.forEach {
            Scheduler.schedule(c, it.id, maxOf(it.time, System.currentTimeMillis() + 60_000))
        }
    }
}
