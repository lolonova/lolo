package fr.moncoach90.app

import android.content.Context
import android.os.Bundle
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern 
import kotlin.math.roundToInt

private val Navy = Color(0xFF10152B)
private val Panel = Color(0xFF1B2341)
private val Blue = Color(0xFF57B8FF)
private val Purple = Color(0xFF9B7BFF)
private val Mint = Color(0xFF55E6C1)
private val Orange = Color(0xFFFFB45C)
private val WhiteSoft = Color(0xFFEAF0FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Coach90App(this) }
    }
}

private data class WeightEntry(val date: String, val kg: Float)
private data class MealEntry(val date: String, val meal: String, val food: String, val quantity: String, val category: String)

private fun today(): String = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
private fun prefs(context: Context) = context.getSharedPreferences("coach90_v6", Context.MODE_PRIVATE)

@Composable
private fun Coach90App(context: Context) {
    val p = remember { prefs(context) }
    var tab by remember { mutableIntStateOf(0) }
    var weights by remember { mutableStateOf(loadWeights(p)) }
    var meals by remember { mutableStateOf(loadMeals(p)) }
    var mood by remember { mutableIntStateOf(p.getInt("mood", 3)) }
    var energy by remember { mutableIntStateOf(p.getInt("energy", 3)) }
    var water by remember { mutableIntStateOf(p.getInt("water", 0)) }
    var chat by remember { mutableStateOf(listOf("Néo" to "Salut ! Je suis Néo 🤖 Ton compagnon de route. Ici, pas de jugement : on avance à ton rythme.")) }
    var chatInput by remember { mutableStateOf("") }
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightDraft by remember { mutableStateOf("") }
    var showMealDialog by remember { mutableStateOf(false) }
    var mealDraftName by remember { mutableStateOf("") }
    var mealDraftType by remember { mutableStateOf("Goûter") }
    var showBoost by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf("") }
    val latest = weights.lastOrNull()?.kg ?: 110f
    val start = weights.firstOrNull()?.kg ?: latest

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
            }
        }
    }
    DisposableEffect(tts) { onDispose { tts.stop(); tts.shutdown() } }
    fun speak(message: String) {
        try { if (ttsReady) { tts.language = Locale.FRANCE; tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "neo_${System.currentTimeMillis()}") } } catch (_: Exception) { }
    }

    fun addNeoMessage(message: String, say: Boolean = true) {
        chat = chat + ("Néo" to message)
        if (say) speak(message)
    }

    fun handleCommand(raw: String) {
        val command = raw.trim()
        if (command.isBlank()) return
        chat = chat + ("Toi" to command)
        val normalized = command.lowercase(Locale.FRANCE)
        val weightMatch = Regex("(\\d{2,3}(?:[,.]\\d)?)\\s*(?:kg|kilo|kilos)?").find(normalized)
        when {
            ("poids" in normalized || "peser" in normalized || "balance" in normalized) && weightMatch != null -> {
                weightDraft = weightMatch.groupValues[1].replace(',', '.')
                showWeightDialog = true
                addNeoMessage("J'ai préparé la saisie à ${weightDraft.replace('.', ',')} kg. Vérifie la valeur puis confirme pour l'enregistrer. 💙")
            }
            "poids" in normalized || "peser" in normalized -> {
                weightDraft = latest.toString().replace('.', ',')
                showWeightDialog = true
                addNeoMessage("Bien sûr ! Indique ton poids dans la fenêtre qui vient de s'ouvrir, puis confirme.")
            }
            listOf("repas", "goûter", "gouter", "déjeuner", "dejeuner", "dîner", "diner", "petit-déjeuner", "ajoute", "ajouter", "note").any { it in normalized } -> {
                mealDraftType = when {
                    "petit-déjeuner" in normalized -> "Petit-déjeuner"
                    "déjeuner" in normalized || "dejeuner" in normalized -> "Déjeuner"
                    "dîner" in normalized || "diner" in normalized -> "Dîner"
                    "goûter" in normalized || "gouter" in normalized -> "Goûter"
                    else -> "Goûter"
                }
                val foodMatch = Regex("(?:ajoute|ajouter|note|enregistre)\\s+(.+?)(?:\\s+(?:au|à la|pour le|pour la)\\s+(?:goûter|gouter|déjeuner|dejeuner|dîner|diner|petit-déjeuner))?$").find(normalized)
                mealDraftName = foodMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: ""
                tab = 1; showMealDialog = true
                addNeoMessage(if (mealDraftName.isNotBlank()) "J'ai préparé ton entrée « $mealDraftName ». Vérifie-la et indique la quantité avant de l'ajouter. 🍎" else "Je t'ouvre le journal des repas. Tu pourras préciser l'aliment et la quantité. 🍎")
            }
            listOf("évolution", "evolution", "progrès", "progres", "courbe").any { it in normalized } -> {
                tab = 2; addNeoMessage("Voici ton évolution. Regarde surtout la tendance dans le temps, pas une mesure isolée. 📈")
            }
            listOf("équilibre", "equilibre", "humeur", "énergie", "energie", "moral").any { it in normalized } -> {
                tab = 3; addNeoMessage("On prend un petit moment pour faire le point sur ton humeur et ton énergie. 🌈")
            }
            listOf("boost", "motiver", "motivation", "activité", "activite", "sortie").any { it in normalized } -> {
                showBoost = true; addNeoMessage("Allez, trouvons une petite chose agréable à faire. Sans pression ! ⚡")
            }
            listOf("parler", "discuter", "confier", "écoute", "ecoute").any { it in normalized } -> {
                tab = 4; addNeoMessage("Je suis là. Prends ton temps, je t'écoute. 🤗")
            }
            else -> {
                tab = 4
                val answer = neoReply(command)
                addNeoMessage(answer)
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == ComponentActivity.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) handleCommand(spoken) else voiceError = "Je n'ai pas bien entendu. Tu peux réessayer."
        } else {
            voiceError = "La reconnaissance vocale n'a pas démarré. Tu peux aussi écrire à Néo."
        }
    }
    fun startVoice() {
        voiceError = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à Néo…")
        }
        try { speechLauncher.launch(intent) } catch (_: Exception) { voiceError = "Aucun service de reconnaissance vocale disponible sur ce téléphone." }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, secondary = Purple, background = Navy, surface = Panel)) {
        Scaffold(
            containerColor = Navy,
            floatingActionButton = {
                if (tab != 4) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallFloatingActionButton(onClick = { startVoice() }, containerColor = Mint, contentColor = Navy) { Icon(Icons.Default.Mic, "Parler à Néo") }
                    ExtendedFloatingActionButton(onClick = { tab = 4 }, containerColor = Purple, contentColor = Color.White, icon = { Text("🤖") }, text = { Text("Néo") })
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                    val labels = listOf("Accueil", "Repas", "Poids", "Équilibre", "Néo")
                    val icons = listOf(Icons.Default.Home, Icons.Default.Restaurant, Icons.Default.MonitorWeight, Icons.Default.Favorite, Icons.Default.Chat)
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], label) }, label = { Text(label, fontSize = 10.sp) })
                    }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> HomeScreen(
                    onVoice = { startVoice() }, onTalk = { tab = 4 },
                    onMeals = { tab = 1 }, onBalance = { tab = 3 }, onEvolution = { tab = 2 },
                    onBoost = { showBoost = true }, padding = padding
                )
                1 -> MealsScreen(meals, onAdd = { showMealDialog = true }, onDelete = { meals = meals - it; saveMeals(p, meals) }, padding = padding)
                2 -> WeightScreen(weights, latest, start, onAdd = { weightDraft = latest.toString().replace('.', ','); showWeightDialog = true }, padding = padding)
                3 -> BalanceScreen(mood, energy, water, onMood = { mood = it; p.edit().putInt("mood", mood).apply() }, onEnergy = { energy = it; p.edit().putInt("energy", energy).apply() }, onWater = { water++; p.edit().putInt("water", water).apply() }, padding = padding)
                else -> NeoScreen(chat, chatInput, onInput = { chatInput = it }, onSend = {
                    if (chatInput.isNotBlank()) handleCommand(chatInput)
                    chatInput = ""
                }, onVoice = { startVoice() }, padding = padding)
            }
            if (voiceError.isNotBlank()) {
                Surface(Modifier.fillMaxWidth().padding(12.dp), color = Panel, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(voiceError, color = WhiteSoft, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = { voiceError = "" }) { Text("OK") }
                    }
                }
            }
        }
    }

    if (showWeightDialog) {
        var value by remember { mutableStateOf(weightDraft.ifBlank { latest.toString().replace('.', ',') }) }
        AlertDialog(onDismissRequest = { showWeightDialog = false }, title = { Text("Ajouter mon poids") },
            text = { OutlinedTextField(value, { value = it }, label = { Text("Poids en kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) },
            confirmButton = { TextButton(onClick = {
                value.replace(',', '.').toFloatOrNull()?.let { kg ->
                    weights = weights + WeightEntry(today(), kg); saveWeights(p, weights)
                    addNeoMessage("C'est enregistré : ${"%.1f".format(Locale.FRANCE, kg)} kg. Merci de prendre ce moment pour toi. Chaque donnée est juste un repère, pas une note. 💙")
                }
                showWeightDialog = false
            }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { showWeightDialog = false }) { Text("Annuler") } })
    }
    if (showMealDialog) {
        var meal by remember { mutableStateOf(mealDraftType) }; var food by remember { mutableStateOf(mealDraftName) }; var qty by remember { mutableStateOf("1 portion") }; var category by remember { mutableStateOf("Autre") }
        AlertDialog(onDismissRequest = { showMealDialog = false }, title = { Text("Ajouter au journal") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var expanded by remember { mutableStateOf(false) }
                Box { OutlinedButton(onClick = { expanded = true }) { Text(meal) }; DropdownMenu(expanded, { expanded = false }) { listOf("Petit-déjeuner","Déjeuner","Goûter","Dîner","Extra").forEach { DropdownMenuItem(text={Text(it)}, onClick={meal=it;expanded=false}) } } }
                OutlinedTextField(food,{food=it},label={Text("Aliment / plat")},singleLine=true)
                OutlinedTextField(qty,{qty=it},label={Text("Quantité (ex. 150 g)")},singleLine=true)
                var catExpanded by remember { mutableStateOf(false) }
                Box { OutlinedButton(onClick={catExpanded=true}) { Text("Famille : $category") }; DropdownMenu(catExpanded,{catExpanded=false}) { listOf("Féculents","Légumes","Protéines","Fruit","Produit laitier","Matières grasses","Autre").forEach { DropdownMenuItem(text={Text(it)},onClick={category=it;catExpanded=false}) } } }
            } },
            confirmButton = { TextButton(onClick={ if(food.isNotBlank()){meals=meals+MealEntry(today(),meal,food,qty,category);saveMeals(p,meals);addNeoMessage("C'est noté dans ton journal. Merci ! 🍎")};showMealDialog=false }) { Text("Ajouter") } },
            dismissButton = { TextButton(onClick={showMealDialog=false}) { Text("Annuler") } })
    }
    if (showBoost) AlertDialog(onDismissRequest={showBoost=false}, title={Text("Petit BOOST ⚡")}, text={Column { Text("Pas besoin de tout changer. Choisis une petite action qui te fait du bien."); Spacer(Modifier.height(8.dp)); listOf("5 minutes dehors","Un message à un ami","Un petit tour à vélo","Un moment avec les enfants","10 minutes rien que pour moi").forEach { TextButton(onClick={showBoost=false;tab=4;addNeoMessage("Ton BOOST du jour : $it 🌟")}) { Text(it) } } } }, confirmButton={TextButton(onClick={showBoost=false}){Text("Plus tard")}})
}

private fun loadWeights(p: android.content.SharedPreferences): List<WeightEntry> = try { val a=JSONArray(p.getString("weights","[]")); (0 until a.length()).map { val o=a.getJSONObject(it); WeightEntry(o.getString("date"),o.getDouble("kg").toFloat()) } } catch(_:Exception){ emptyList() }
private fun saveWeights(p: android.content.SharedPreferences, list:List<WeightEntry>) { val a=JSONArray(); list.forEach { a.put(JSONObject().put("date",it.date).put("kg",it.kg)) }; p.edit().putString("weights",a.toString()).apply() }
private fun loadMeals(p: android.content.SharedPreferences):List<MealEntry> = try { val a=JSONArray(p.getString("meals","[]")); (0 until a.length()).map { val o=a.getJSONObject(it); MealEntry(o.getString("date"),o.getString("meal"),o.getString("food"),o.getString("quantity"),o.getString("category")) } } catch(_:Exception){emptyList()}
private fun saveMeals(p:android.content.SharedPreferences,list:List<MealEntry>){val a=JSONArray();list.forEach{a.put(JSONObject().put("date",it.date).put("meal",it.meal).put("food",it.food).put("quantity",it.quantity).put("category",it.category))};p.edit().putString("meals",a.toString()).apply()}

@Composable private fun ScreenColumn(title:String, subtitle:String, padding:PaddingValues, content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp).verticalScrollCompat(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp)); Text(title,fontSize=27.sp,fontWeight=FontWeight.ExtraBold,color=WhiteSoft); Text(subtitle,color=Color.LightGray,fontSize=14.sp); content(); Spacer(Modifier.height(18.dp))
    }
}
// A small helper keeps screens scrollable without requiring a separate layout file.
@Composable private fun Modifier.verticalScrollCompat(): Modifier = this.verticalScroll(rememberScrollState())

@Composable private fun HomeScreen(
    onVoice: () -> Unit, onTalk: () -> Unit, onMeals: () -> Unit,
    onBalance: () -> Unit, onEvolution: () -> Unit, onBoost: () -> Unit,
    padding: PaddingValues
) {
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Bonjour ! Ravi de te retrouver. On commence tranquillement ? ☀️"
        in 12..17 -> "Coucou ! Comment se passe ta journée ? 😊"
        else -> "Bonsoir ! Tu peux souffler, je suis là. 🌙"
    }
    val transition = rememberInfiniteTransition(label = "neo_bob")
    val bob by transition.animateFloat(initialValue = 0f, targetValue = 8f, animationSpec = infiniteRepeatable(animation = tween(1100, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "neo_float")
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("MON COACH 90", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(126.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF293D73), Color(0xFF654A9D)))), contentAlignment = Alignment.Center) {
                    Text("🤖", fontSize = 76.sp, modifier = Modifier.graphicsLayer { translationY = bob.dp.toPx() })
                    Surface(Modifier.align(Alignment.BottomEnd), color = Mint, shape = CircleShape) { Text("•", color = Navy, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp)) }
                }
                Text("Salut, c'est Néo !", color = WhiteSoft, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text(greeting, color = WhiteSoft, fontSize = 16.sp)
                Text("Ici, pas de jugement ni de pression. On avance à ta façon.", color = Color.LightGray, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onVoice, colors = ButtonDefaults.buttonColors(containerColor = Mint), modifier = Modifier.weight(1f)) { Icon(Icons.Default.Mic, null); Text("  Salut Néo !") }
                    OutlinedButton(onClick = onTalk, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Chat, null); Text("  Écrire") }
                }
            }
        }
        Text("Qu'est-ce qui te ferait du bien maintenant ?", color = WhiteSoft, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("🍽️", "Mes repas", "Noter ou préparer", Blue, onMeals, Modifier.weight(1f))
            ActionTile("⚡", "Un BOOST", "Bouger ou souffler", Orange, onBoost, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("🌈", "Mon équilibre", "Humeur et énergie", Mint, onBalance, Modifier.weight(1f))
            ActionTile("📈", "Mes progrès", "Voir mon chemin", Purple, onEvolution, Modifier.weight(1f))
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF263052)), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💙", fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Le petit rappel de Néo", color = Mint, fontWeight = FontWeight.Bold)
                    Text("Tu as le droit d'avancer doucement. Prendre soin de toi, c'est aussi profiter, rire, voir tes proches et te reposer.", color = WhiteSoft, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable private fun ActionTile(emoji: String, title: String, subtitle: String, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(emoji, fontSize = 27.sp)
            Text(title, color = tint, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = Color.LightGray, fontSize = 11.sp)
        }
    }
}

@Composable private fun WeightScreen(weights:List<WeightEntry>,latest:Float,start:Float,onAdd:()->Unit,padding:PaddingValues){
    ScreenColumn("Mon évolution 📈","Chaque mesure raconte ton chemin.",padding){CardBox { Text("POIDS ACTUEL",color=Blue,fontWeight=FontWeight.Bold);Text("%.1f kg".format(Locale.FRANCE,latest),fontSize=34.sp,fontWeight=FontWeight.Bold);Text("Évolution : ${if(start-latest>=0)"−" else "+"}${"%.1f".format(Locale.FRANCE,kotlin.math.abs(start-latest))} kg",color=Mint);Button(onClick=onAdd){Text("+ Ajouter une mesure")} }
        CardBox { Text("COURBE (toutes les mesures)",fontWeight=FontWeight.Bold,color=WhiteSoft);if(weights.size>1) WeightChart(weights) else Text("Ajoute plusieurs mesures pour voir apparaître ta courbe.",color=Color.LightGray);Spacer(Modifier.height(10.dp));weights.takeLast(12).reversed().forEach{Text("${it.date}     %.1f kg".format(Locale.FRANCE,it.kg),color=WhiteSoft)} }
        CardBox { Text("🎯 Cap sur ton objectif",fontWeight=FontWeight.Bold,color=Orange);Text("On avance progressivement. La régularité compte davantage qu'une variation isolée.",color=WhiteSoft) }
    }
}
@Composable private fun WeightChart(entries:List<WeightEntry>){Canvas(Modifier.fillMaxWidth().height(150.dp)){val data=entries.takeLast(12).map{it.kg};if(data.size>1){val min=data.minOrNull()!!-1;val max=data.maxOrNull()!!+1;val pts=data.mapIndexed{i,v-> androidx.compose.ui.geometry.Offset(size.width*i/(data.size-1),size.height-(v-min)/(max-min)*size.height)};for(i in 0 until pts.lastIndex)drawLine(Blue,pts[i],pts[i+1],5f);pts.forEach{drawCircle(Mint,5f,it)}}}}
@Composable private fun MealsScreen(meals:List<MealEntry>,onAdd:()->Unit,onDelete:(MealEntry)->Unit,padding:PaddingValues){
    ScreenColumn("Mon journal 🍽️","On observe, on apprend, sans culpabiliser.",padding){CardBox{Text("Aujourd'hui · ${meals.count{it.date==today()}} entrée(s)",fontWeight=FontWeight.Bold,color=Mint);Button(onClick=onAdd){Icon(Icons.Default.Add,null);Text(" Ajouter un repas")};Text("Les quantités et les calories ne sont pas calculées automatiquement : l'important ici est de garder une trace simple.",color=Color.LightGray,fontSize=12.sp)}
        meals.filter{it.date==today()}.reversed().forEach{m->CardBox{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("${m.meal} · ${m.category}",color=Orange,fontWeight=FontWeight.Bold);Text(m.food,color=WhiteSoft,fontSize=17.sp);Text(m.quantity,color=Color.LightGray)};IconButton(onClick={onDelete(m)}){Icon(Icons.Default.Delete,null,tint=Color.LightGray)}}}}
        CardBox{Text("💡 Petit repère",fontWeight=FontWeight.Bold,color=Blue);Text("Essaie d'ajouter une source de protéines, des légumes ou un fruit quand cela te convient. Pas de règle rigide : on cherche l'équilibre sur la durée.",color=WhiteSoft)}
    }
}
@Composable private fun BalanceScreen(mood:Int,energy:Int,water:Int,onMood:(Int)->Unit,onEnergy:(Int)->Unit,onWater:()->Unit,padding:PaddingValues){
    ScreenColumn("Mon équilibre 🌈","Ton bien-être ne se résume pas à la balance.",padding){CardBox{Text("COMMENT TU TE SENS ?",color=Purple,fontWeight=FontWeight.Bold);Text("Humeur",color=WhiteSoft);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("😞","😐","🙂","😄","🤩").forEachIndexed{i,s->Text(s,fontSize=25.sp,modifier=Modifier.clip(CircleShape).background(if(mood==i+1)Purple else Navy).clickable{onMood(i+1)}.padding(8.dp))}};Spacer(Modifier.height(12.dp));Text("Énergie",color=WhiteSoft);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..5).forEach{i->Text(if(i<=energy)"⚡" else "·",fontSize=25.sp,modifier=Modifier.clickable{onEnergy(i)}.padding(4.dp))}}}
        CardBox{Text("💧 Hydratation",fontWeight=FontWeight.Bold,color=Blue);Text("$water verre(s) noté(s) aujourd'hui",color=WhiteSoft);Button(onClick=onWater){Text("+ Ajouter un verre")}}
        CardBox{Text("🌟 Une victoire qui n'est pas un chiffre",fontWeight=FontWeight.Bold,color=Mint);Text("Avoir pris l'air, appelé un ami, joué avec les enfants, pris du temps pour toi ou simplement traversé une journée difficile : tout cela compte.",color=WhiteSoft)}
        CardBox{Text("🧡 Ton espace",fontWeight=FontWeight.Bold,color=Orange);Text("Tu as le droit de souffler, de voir tes amis, de profiter de ta famille et de ne rien faire. Le repos fait partie du chemin.",color=WhiteSoft)}
    }
}
@Composable private fun NeoScreen(chat:List<Pair<String,String>>,input:String,onInput:(String)->Unit,onSend:()->Unit,onVoice:()->Unit,padding:PaddingValues){
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Text("🤖", fontSize=36.sp); Spacer(Modifier.width(10.dp)); Column { Text("Néo",fontSize=27.sp,fontWeight=FontWeight.ExtraBold,color=WhiteSoft); Text("Ton compagnon, sans jugement.",color=Color.LightGray,fontSize=12.sp) } }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){items(chat){(who,msg)->Row(Modifier.fillMaxWidth(),horizontalArrangement=if(who=="Toi")Arrangement.End else Arrangement.Start){Surface(color=if(who=="Toi")Color(0xFF263D67) else Panel,shape=RoundedCornerShape(16.dp)){Column(Modifier.widthIn(max=300.dp).padding(12.dp)){Text(who,color=if(who=="Toi")Blue else Mint,fontWeight=FontWeight.Bold,fontSize=12.sp);Text(msg,color=WhiteSoft)}}}}}
        Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(input,onInput,Modifier.weight(1f),placeholder={Text("Écris ou donne une directive à Néo…")},maxLines=3);IconButton(onClick=onVoice){Icon(Icons.Default.Mic,"Parler",tint=Mint)};IconButton(onClick=onSend){Icon(Icons.Default.Send,"Envoyer",tint=Blue)}}
        Text("Tu peux demander une action : noter ton poids, ouvrir les repas, voir tes progrès ou simplement discuter. Les commandes sont traitées sur le téléphone dans cette version.",fontSize=10.sp,color=Color.Gray)
    }
}
private fun neoReply(text:String):String {val t=text.lowercase(Locale.FRANCE);return when{listOf("craque","mangé","mange","grignot","écart").any{it in t}->"Ça arrive, et ça ne gâche rien. Pas de punition ni de compensation : reprends simplement au prochain repas. Qu'est-ce qui t'a donné envie de manger ?";listOf("fatigu","stress","dur","mal","pas bien").any{it in t}->"Je t'entends. Là, l'objectif n'est pas de performer : choisis une toute petite chose qui te soulage. Un verre d'eau, respirer, te poser ou appeler quelqu'un ?";listOf("poids","maigr","objectif").any{it in t}->"On vise la régularité, pas la perfection. Le poids peut bouger d'un jour à l'autre : regarde plutôt la tendance sur plusieurs semaines.";else->"Merci de me le confier 💙. On peut faire simple : de quoi aurais-tu le plus besoin maintenant — motivation, idée de repas, petit BOOST ou juste parler ?"}}
@Composable private fun CardBox(content:@Composable ColumnScope.()->Unit){Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
