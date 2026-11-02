package fr.moncoach90.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

private val Ink = Color(0xFF17243A)
private val Blue = Color(0xFF4169E1)
private val Mint = Color(0xFF20B89A)
private val Pale = Color(0xFFF3F6FC)
private const val PREFS = "coach90_local"
private const val CHANNEL = "coach90_motivation"

class MainActivity : ComponentActivity() {
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) CoachNotifications.createChannel(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoachNotifications.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        CoachNotifications.scheduleDaily(this, 16, 30, 1)
        CoachNotifications.scheduleDaily(this, 18, 15, 2)
        setContent { CoachApp(this) }
    }
}

@Composable
fun CoachApp(context: Context) {
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var page by remember { mutableStateOf(0) }
    var weight by remember { mutableStateOf(prefs.getFloat("weight", 110f).toString()) }
    var water by remember { mutableIntStateOf(prefs.getInt("water", 0)) }
    var steps by remember { mutableIntStateOf(prefs.getInt("steps", 0)) }
    var meal by remember { mutableStateOf("") }
    var meals by remember { mutableStateOf(prefs.getString("meals", "") ?: "") }
    var cravingOpen by remember { mutableStateOf(false) }
    var mood by remember { mutableStateOf(prefs.getString("mood", "En forme") ?: "En forme") }
    var message by remember { mutableStateOf("Chaque petit choix compte. Aujourd'hui, on avance sans chercher la perfection.") }

    MaterialTheme(
        colorScheme = lightColorScheme(primary = Blue, secondary = Mint, background = Pale)
    ) {
        Scaffold(
            containerColor = Pale,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf("Accueil", "Repas", "Progrès", "Coach").forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = page == i,
                            onClick = { page = i },
                            icon = {
                                Icon(
                                    when (i) {
                                        0 -> Icons.Default.Home
                                        1 -> Icons.Default.Restaurant
                                        2 -> Icons.Default.Insights
                                        else -> Icons.Default.Favorite
                                    }, contentDescription = label
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            when (page) {
                0 -> HomeScreen(
                    context, weight, { weight = it; it.toFloatOrNull()?.let { v -> prefs.edit().putFloat("weight", v).apply() } },
                    water, { water = it; prefs.edit().putInt("water", it).apply() },
                    steps, { steps = it; prefs.edit().putInt("steps", it).apply() },
                    mood, { mood = it; prefs.edit().putString("mood", it).apply() },
                    { cravingOpen = true }, padding
                )
                1 -> MealsScreen(meal, { meal = it }, meals, {
                    meals = it; prefs.edit().putString("meals", it).apply()
                }, padding)
                2 -> ProgressScreen(weight, water, steps, meals, padding)
                else -> CoachScreen(message, { message = it }, { cravingOpen = true }, padding)
            }
        }
        if (cravingOpen) {
            AlertDialog(
                onDismissRequest = { cravingOpen = false },
                title = { Text("Pause anti-fringale 🧡") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Avant de manger, prends 5 minutes. La faim est-elle physique, ou est-ce surtout la fatigue / le stress ?")
                        Text("1. Bois un verre d'eau.")
                        Text("2. Respire lentement pendant une minute.")
                        Text("3. Si tu as encore faim, prends une collation choisie : fruit + yaourt, ou une poignée d'amandes.")
                        Text("Un craquage ne gâche rien. On reprend simplement le fil.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { cravingOpen = false }) { Text("Je continue 💪") }
                },
                dismissButton = {
                    TextButton(onClick = { cravingOpen = false }) { Text("J'ai besoin de manger") }
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    context: Context, weight: String, onWeight: (String) -> Unit,
    water: Int, onWater: (Int) -> Unit, steps: Int, onSteps: (Int) -> Unit,
    mood: String, onMood: (String) -> Unit, onCraving: () -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MON COACH 90", color = Blue, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                    Text("On avance à ton rythme.", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.size(66.dp).background(
                        Brush.linearGradient(listOf(Color(0xFFDAE6FF), Color(0xFFD9FFF3))),
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) { Text("🏃‍♂️", fontSize = 34.sp) }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
                Column(Modifier.padding(20.dp)) {
                    Text("TON RENDEZ-VOUS DU JOUR", color = Color(0xFFB9C9F5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Tu n'as pas besoin d'être parfait.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Tu as seulement besoin de continuer. Même doucement.", color = Color(0xFFE0E8F8))
                    Spacer(Modifier.height(12.dp))
                    Text("✨ Coach : pense à préparer une collation pour le retour du travail.", color = Color(0xFFBFF6E8))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("OBJECTIF", "90 kg", "Ton cap", Modifier.weight(1f))
                MetricCard("EAU", "$water ml", "Aujourd'hui", Modifier.weight(1f))
                MetricCard("PAS", steps.toString(), "Aujourd'hui", Modifier.weight(1f))
            }
        }
        item {
            SectionCard("Mon poids") {
                Text("Dernier poids enregistré (kg)", color = Ink)
                OutlinedTextField(
                    value = weight, onValueChange = onWeight, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), suffix = { Text("kg") }
                )
                Text("Objectif : 90 kg • progression enregistrée sur ce téléphone.", color = Color.Gray, fontSize = 12.sp)
            }
        }
        item {
            SectionCard("Hydratation") {
                Text("Verres d'eau : ${water / 250} • Objectif indicatif : 2 000 ml")
                LinearProgressIndicator(progress = { (water / 2000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onWater((water + 250).coerceAtMost(5000)) }) { Text("+ 250 ml") }
                    OutlinedButton(onClick = { onWater((water - 250).coerceAtLeast(0)) }) { Text("− 250 ml") }
                }
            }
        }
        item {
            SectionCard("Activité") {
                Text("Pas effectués aujourd'hui")
                OutlinedTextField(
                    value = steps.toString(), onValueChange = { onSteps(it.toIntOrNull()?.coerceIn(0, 100000) ?: 0) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, suffix = { Text("pas") }
                )
                Text("Saisie manuelle dans cette première base V5. La lecture automatique des capteurs sera ajoutée après validation sur ton téléphone.", color = Color.Gray, fontSize = 12.sp)
            }
        }
        item {
            SectionCard("Comment tu te sens ?") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("🙂 En forme", "😐 Moyen", "😴 Fatigué", "😣 Stressé").forEach { label ->
                        FilterChip(selected = mood == label, onClick = { onMood(label) }, label = { Text(label, fontSize = 11.sp) })
                    }
                }
            }
        }
        item {
            Button(onClick = onCraving, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.SelfImprovement, null)
                Spacer(Modifier.width(8.dp))
                Text("J'ai une fringale — aide-moi !")
            }
        }
    }
}

@Composable
fun MealsScreen(meal: String, onMeal: (String) -> Unit, meals: String, onMeals: (String) -> Unit, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Mon journal alimentaire", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Note simplement ce que tu manges. L'objectif est de comprendre tes habitudes, pas de te juger.")
        OutlinedTextField(meal, onMeal, modifier = Modifier.fillMaxWidth(), label = { Text("Ex. : salade, poulet, pain…") }, minLines = 2)
        Button(onClick = {
            if (meal.isNotBlank()) {
                val stamp = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())
                onMeals("$meals\n$stamp • $meal".trim())
                onMeal("")
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Ajouter le repas") }
        SectionCard("Aujourd'hui") {
            Text(if (meals.isBlank()) "Aucun repas noté pour le moment." else meals, color = Ink)
        }
        SectionCard("Analyse photo et estimation nutritionnelle") {
            Text("Prévue dans l'architecture V5, mais nécessite un service d'analyse connecté à Internet. Aucun résultat calorique fictif n'est affiché dans cette version.")
        }
    }
}

@Composable
fun ProgressScreen(weight: String, water: Int, steps: Int, meals: String, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Mes progrès", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
        SectionCard("Ton cap") {
            Text("Objectif final : 90 kg", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Dernier poids saisi : ${weight} kg")
            Text("Hydratation : $water ml")
            Text("Pas saisis : $steps")
        }
        SectionCard("Régularité") {
            Text("Les données de cette version sont conservées localement sur le téléphone.")
            Text("L'historique daté et les courbes seront activés dans la prochaine itération, après validation de la compatibilité avec la version précédente.")
        }
        SectionCard("Journal") {
            Text(if (meals.isBlank()) "Pas encore de repas enregistré." else meals)
        }
    }
}

@Composable
fun CoachScreen(message: String, onMessage: (String) -> Unit, onCraving: () -> Unit, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ton coach", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🧑‍🚀", fontSize = 48.sp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Coach 90", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Bienveillant, jamais culpabilisant.")
                }
            }
        }
        SectionCard("Ton message du moment") { Text(message) }
        Button(onClick = {
            onMessage(listOf(
                "Tu as déjà fait le premier pas. Aujourd'hui, vise une seule bonne décision à la fois.",
                "Le retour du travail est un moment sensible : anticipe avec une collation et un grand verre d'eau.",
                "Un repas plus riche n'efface pas tes efforts. Le prochain choix compte davantage que le précédent.",
                "Bouger un peu vaut mieux que ne rien faire. Une courte marche est déjà une victoire."
            ).random())
        }, modifier = Modifier.fillMaxWidth()) { Text("✨ Un conseil motivant") }
        OutlinedButton(onClick = onCraving, modifier = Modifier.fillMaxWidth()) { Text("Lancer le mode anti-fringale") }
    }
}

@Composable
fun MetricCard(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color.Gray, fontSize = 10.sp)
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    content = content
) {            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
    }
}

object CoachNotifications {
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Coach et motivation", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Rappels bienveillants de Mon Coach 90"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun scheduleDaily(context: Context, hour: Int, minute: Int, id: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CoachNotificationReceiver::class.java).putExtra("id", id)
        val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }
}

class CoachNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoachNotifications.createChannel(context)
        val id = intent.getIntExtra("id", 1)
        val title = if (id == 1) "Le petit rappel de 16 h 30 🌿" else "Le rendez-vous du soir 💪"
        val body = if (id == 1) "Prévois ta collation avant que la fringale décide pour toi." else "Tu rentres ? Fais une pause, bois un verre d'eau et écoute ta faim."
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true).build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoachNotifications.scheduleDaily(context, 16, 30, 1)
            CoachNotifications.scheduleDaily(context, 18, 15, 2)
        }
    }
}
