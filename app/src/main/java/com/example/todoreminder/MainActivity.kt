package com.example.todoreminder

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { MaterialTheme { TodoScreen() } }
    }
}

private val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

@Composable
fun TodoScreen() {
    val ctx = LocalContext.current
    val tasks = remember { mutableStateListOf<Task>().apply { addAll(Store.load(ctx)) } }
    var title by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Long?>(null) }

    fun persist() = Store.save(ctx, tasks.toList())

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("My To-Do List", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            label = { Text("New task") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickDateTime(ctx) { picked = it } }) {
                Text(picked?.let { fmt.format(Date(it)) } ?: "Set reminder time")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (title.isNotBlank()) {
                    val t = Task(System.currentTimeMillis(), title.trim(), picked ?: 0L, false)
                    tasks.add(0, t); persist()
                    if (t.time > System.currentTimeMillis()) Scheduler.schedule(ctx, t.id, t.time)
                    title = ""; picked = null
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(tasks, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = t.done, onCheckedChange = { checked ->
                        val i = tasks.indexOfFirst { it.id == t.id }
                        tasks[i] = t.copy(done = checked); persist()
                        if (checked) Scheduler.cancel(ctx, t.id)
                        else if (t.time > 0) Scheduler.schedule(
                            ctx, t.id, maxOf(t.time, System.currentTimeMillis() + 5000))
                    })
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            textDecoration = if (t.done) TextDecoration.LineThrough else null
                        )
                        if (t.time > 0) Text(
                            "⏰ " + fmt.format(Date(t.time)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = {
                        Scheduler.cancel(ctx, t.id); tasks.remove(t); persist()
                    }) { Text("Delete") }
                }
            }
        }
    }
}

fun pickDateTime(ctx: Context, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance()
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, h, min ->
            c.set(y, m, d, h, min, 0)
            onPicked(c.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}
