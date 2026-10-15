
package com.example.flashcardquiz

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// --------------------------------------------------
// AESTHETIC WHITE + PASTEL THEME
// --------------------------------------------------

private val Lavender = Color(0xFF8171D5)
private val LavenderLight = Color(0xFFEDE8FF)
private val Mint = Color(0xFF54B99B)
private val MintLight = Color(0xFFE2F6EE)
private val Peach = Color(0xFFE9A66F)
private val PeachLight = Color(0xFFFFF0E2)
private val Ink = Color(0xFF29263A)
private val Muted = Color(0xFF777488)
private val PageWhite = Color(0xFFF8F7FC)

private val LightColors = lightColorScheme(
    primary = Lavender,
    onPrimary = Color.White,
    secondary = Mint,
    tertiary = Peach,
    background = PageWhite,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EDF8),
    onSurfaceVariant = Muted,
    secondaryContainer = LavenderLight,
    onSecondaryContainer = Ink
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9ACFF),
    secondary = Color(0xFF8CE0C5),
    tertiary = Color(0xFFFFC99C),
    background = Color(0xFF171522),
    surface = Color(0xFF242132),
    onSurface = Color(0xFFF5F1FF),
    secondaryContainer = Color(0xFF37304F)
)

// --------------------------------------------------
// DATA MODEL
// --------------------------------------------------

data class Flashcard(
    val id: Int,
    val question: String,
    val answer: String,
    val category: String = "General",
    val favorite: Boolean = false,
    val difficulty: String = "Medium"
)

// --------------------------------------------------
// MAIN ACTIVITY
// --------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val prefs = remember {
                getSharedPreferences("studyspace", Context.MODE_PRIVATE)
            }

            var darkMode by remember {
                mutableStateOf(prefs.getBoolean("dark", false))
            }

            MaterialTheme(
                colorScheme = if (darkMode) DarkColors else LightColors
            ) {
                StudySpaceApp(
                    darkMode = darkMode,
                    onDarkModeChange = {
                        darkMode = it
                        prefs.edit().putBoolean("dark", it).apply()
                    }
                )
            }
        }
    }
}

// --------------------------------------------------
// APP
// --------------------------------------------------

@Composable
fun StudySpaceApp(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val prefs = remember {
        context.getSharedPreferences("studyspace", Context.MODE_PRIVATE)
    }

    var cards by remember { mutableStateOf(loadCards(context)) }
    var page by remember { mutableStateOf("Home") }

    var currentIndex by remember { mutableIntStateOf(0) }
    var answerShown by remember { mutableStateOf(false) }

    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var difficulty by remember { mutableStateOf("Medium") }
    var showAddDialog by remember { mutableStateOf(false) }

    var dates by remember {
        mutableStateOf(
            prefs.getStringSet("dates", emptySet())?.toSet() ?: emptySet()
        )
    }

    var stats by remember { mutableStateOf(loadStats(context)) }

    var goal by remember {
        mutableIntStateOf(prefs.getInt("goal", 10))
    }

    var goalDate by remember {
        mutableStateOf(prefs.getString("goalDate", "") ?: "")
    }

    var reviewedToday by remember {
        mutableIntStateOf(
            if (goalDate == today()) prefs.getInt("reviewed", 0) else 0
        )
    }

    // Quiz state
    var quizPool by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var quizOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var quizIndex by remember { mutableIntStateOf(0) }
    var quizScore by remember { mutableIntStateOf(0) }
    var quizActive by remember { mutableStateOf(false) }
    var quizFinished by remember { mutableStateOf(false) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableIntStateOf(20) }
    var quizDifficulty by remember { mutableStateOf("All") }
    var quizLength by remember { mutableIntStateOf(5) }
    var scoreSaved by remember { mutableStateOf(false) }

    var leaderboard by remember {
        mutableStateOf(prefs.getString("leaderboard", "") ?: "")
    }

    var message by remember { mutableStateOf("") }
    var dragAmount by remember { mutableFloatStateOf(0f) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    // Text-to-speech lifecycle
    DisposableEffect(context) {
        var engine: TextToSpeech? = null

        engine = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS

            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
            }
        }

        tts = engine

        onDispose {
            engine?.stop()
            engine?.shutdown()
            tts = null
        }
    }

    // Export JSON
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(cardsToJson(cards).toString(2))
                }
                message = "Flashcards exported successfully."
            } catch (e: Exception) {
                message = "Export failed: ${e.message}"
            }
        }
    }

    // Import JSON
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }

                if (json != null) {
                    val imported = parseCards(json)
                    var nextId = (cards.maxOfOrNull { it.id } ?: 0) + 1

                    val additions = imported.map { card ->
                        card.copy(id = nextId++)
                    }

                    cards = cards + additions
                    saveCards(context, cards)
                    message = "${additions.size} flashcards imported."
                }
            } catch (_: Exception) {
                message = "Import failed. Choose a valid JSON file."
            }
        }
    }

    fun recordStudy(card: Flashcard, correct: Boolean) {
        val now = today()

        if (goalDate != now) {
            goalDate = now
            reviewedToday = 0
        }

        reviewedToday++
        dates = dates + now

        prefs.edit()
            .putStringSet("dates", dates)
            .putString("goalDate", now)
            .putInt("reviewed", reviewedToday)
            .apply()

        val previous = stats[card.category] ?: (0 to 0)

        stats = stats + (
                card.category to (
                        (previous.first + if (correct) 1 else 0) to
                                (previous.second + 1)
                        )
                )

        saveStats(context, stats)
    }

    fun createOptions(card: Flashcard, pool: List<Flashcard>): List<String> {
        return (
                listOf(card.answer) +
                        pool.filter { it.id != card.id }
                            .map { it.answer }
                            .distinct()
                            .shuffled()
                            .take(3)
                ).distinct().shuffled()
    }

    fun saveQuizScore() {
        if (scoreSaved) return

        val percentage = if (quizPool.isNotEmpty()) {
            quizScore * 100 / quizPool.size
        } else 0

        val scores = (
                leaderboard.split(",").mapNotNull { it.toIntOrNull() } +
                        percentage
                ).sortedDescending().take(10)

        leaderboard = scores.joinToString(",")
        prefs.edit().putString("leaderboard", leaderboard).apply()
        scoreSaved = true
    }

    fun startQuiz() {
        val eligible = cards.filter {
            quizDifficulty == "All" || it.difficulty == quizDifficulty
        }.shuffled()

        quizPool = eligible.take(quizLength.coerceAtLeast(1))
        quizIndex = 0
        quizScore = 0
        secondsLeft = 20
        selectedAnswer = null
        quizFinished = false
        scoreSaved = false
        quizActive = quizPool.isNotEmpty()

        quizOptions = quizPool.firstOrNull()?.let {
            createOptions(it, quizPool)
        } ?: emptyList()

        page = "Quiz"

        if (quizPool.isEmpty()) {
            message = "No cards match this difficulty. Try All or add cards."
        }
    }

    fun nextQuizQuestion() {
        if (quizIndex < quizPool.lastIndex) {
            quizIndex++
            secondsLeft = 20
            selectedAnswer = null
            quizOptions = createOptions(quizPool[quizIndex], quizPool)
        } else {
            quizActive = false
            quizFinished = true
            saveQuizScore()
        }
    }

    // Quiz timer
    LaunchedEffect(
        quizActive, quizFinished, quizIndex, selectedAnswer, secondsLeft
    ) {
        if (quizActive && !quizFinished && selectedAnswer == null) {
            delay(1000)

            if (secondsLeft > 1) {
                secondsLeft--
            } else {
                selectedAnswer = "__TIMEOUT__"
            }
        }
    }

    // Automatically move on after timeout
    LaunchedEffect(selectedAnswer, quizIndex, quizActive) {
        if (quizActive && selectedAnswer == "__TIMEOUT__") {
            delay(1500)
            nextQuizQuestion()
        }
    }

    val visibleCards = if (page == "Favorites") {
        cards.filter { it.favorite }
    } else {
        cards
    }

    fun nextStudyCard(known: Boolean) {
        val card = visibleCards.getOrNull(currentIndex) ?: return

        recordStudy(card, known)

        currentIndex = if (currentIndex + 1 >= visibleCards.size) {
            0
        } else {
            currentIndex + 1
        }

        answerShown = false
        dragAmount = 0f
    }

    // --------------------------------------------------
    // NAVIGATION AND SCREENS
    // --------------------------------------------------

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                listOf("Home", "Study", "Quiz", "Favorites", "Stats", "Settings")
                    .forEach { destination ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = {
                                page = destination
                                currentIndex = 0
                                answerShown = false
                            },
                            icon = {
                                Text(
                                    when (destination) {
                                        "Home" -> "⌂"
                                        "Study" -> "▤"
                                        "Quiz" -> "◉"
                                        "Favorites" -> "★"
                                        "Stats" -> "▥"
                                        else -> "⚙"
                                    },
                                    fontSize = 18.sp
                                )
                            },
                            label = {
                                Text(destination, fontSize = 9.sp)
                            }
                        )
                    }
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            when (page) {

                // HOME
                "Home" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (darkMode) MaterialTheme.colorScheme.surface
                                else LavenderLight,
                                RoundedCornerShape(28.dp)
                            )
                            .padding(24.dp)
                    ) {
                        Text(
                            "YOUR LEARNING SPACE",
                            color = Lavender,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Hello, Learner 👋",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "A little progress every day.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp
                        )

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = { page = "Study" },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                "Start studying  →",
                                modifier = Modifier.padding(vertical = 5.dp),
                                fontSize = 16.sp
                            )
                        }
                    }

                    DashboardTile(
                        "📚 Flashcards",
                        cards.size.toString(),
                        Mint
                    )

                    DashboardTile(
                        "⭐ Favorites",
                        cards.count { it.favorite }.toString(),
                        Lavender
                    )

                    DashboardTile(
                        "📅 Study days",
                        dates.size.toString(),
                        Peach
                    )

                    DashboardTile(
                        "🎯 Today's goal",
                        "$reviewedToday / $goal",
                        Mint
                    )

                    LinearProgressIndicator(
                        progress = (
                                reviewedToday.toFloat() / goal.coerceAtLeast(1)
                                ).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { page = "Quiz" },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Take a quiz  →")
                    }

                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("＋ Add flashcard")
                    }

                    Text(
                        "Your study calendar",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    StudyCalendar(dates)
                }

                // STUDY AND FAVORITES
                "Study", "Favorites" -> {
                    Text(
                        if (page == "Favorites") "Your Favorites ⭐"
                        else "Study Cards",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (visibleCards.isEmpty()) {
                        Text(
                            if (page == "Favorites")
                                "No favorites yet. Star a card to save it here."
                            else
                                "No flashcards yet. Add your first card from Home."
                        )
                    } else {
                        val card = visibleCards[
                            currentIndex.coerceIn(0, visibleCards.lastIndex)
                        ]

                        Text("Card ${currentIndex + 1} of ${visibleCards.size}")

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 250.dp)
                                .pointerInput(card.id, answerShown) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            if (abs(dragAmount) > 80f) {
                                                nextStudyCard(dragAmount > 0)
                                            }
                                            dragAmount = 0f
                                        },
                                        onDragCancel = { dragAmount = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragAmount += amount.x
                                        }
                                    )
                                },
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(Modifier.padding(24.dp)) {
                                Text(
                                    card.category.uppercase(),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(24.dp))

                                Text(
                                    card.question,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (answerShown) {
                                    Spacer(Modifier.height(20.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(14.dp))
                                    Text(card.answer, fontSize = 18.sp)
                                }

                                Spacer(Modifier.height(18.dp))
                                Text("Swipe right → Know it")
                                Text("← Swipe left → Review again")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(onClick = {
                                cards = cards.map {
                                    if (it.id == card.id) {
                                        it.copy(favorite = !it.favorite)
                                    } else it
                                }
                                saveCards(context, cards)
                            }) {
                                Text(if (card.favorite) "★ Saved" else "☆ Favorite")
                            }

                            Button(onClick = {
                                answerShown = !answerShown
                            }) {
                                Text(if (answerShown) "Hide answer" else "Show answer")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { nextStudyCard(false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Review again")
                            }

                            Button(
                                onClick = { nextStudyCard(true) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("I know this")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (ttsReady) {
                                        tts?.speak(
                                            card.question,
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            "question"
                                        )
                                    } else {
                                        message = "Text-to-speech is unavailable."
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔊 Question")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (ttsReady) {
                                        tts?.speak(
                                            card.answer,
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            "answer"
                                        )
                                    } else {
                                        message = "Text-to-speech is unavailable."
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔊 Answer")
                            }
                        }
                    }
                }

                // QUIZ
                "Quiz" -> {
                    Text(
                        "Quiz Corner 🧠",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (!quizActive && !quizFinished) {
                        Text("Choose difficulty")

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("All", "Easy", "Medium", "Hard").forEach {
                                FilterChip(
                                    selected = quizDifficulty == it,
                                    onClick = { quizDifficulty = it },
                                    label = { Text(it) }
                                )
                            }
                        }

                        Text("Number of questions: $quizLength")

                        Slider(
                            value = quizLength.toFloat(),
                            onValueChange = {
                                quizLength = it.toInt().coerceAtLeast(1)
                            },
                            valueRange = 1f..cards.size.coerceAtLeast(1).toFloat()
                        )

                        Button(
                            onClick = { startQuiz() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start timed quiz")
                        }
                    }

                    if (quizActive) {
                        val card = quizPool.getOrNull(quizIndex)

                        if (card != null) {
                            Text("Question ${quizIndex + 1} / ${quizPool.size}")

                            Text(
                                "Time remaining: $secondsLeft seconds",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            LinearProgressIndicator(
                                progress = secondsLeft / 20f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                card.question,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )

                            quizOptions.forEach { option ->
                                val correct = option == card.answer
                                val chosen = selectedAnswer == option

                                val optionColor = when {
                                    selectedAnswer != null && correct ->
                                        MintLight
                                    chosen -> PeachLight
                                    else -> MaterialTheme.colorScheme.surface
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = selectedAnswer == null) {
                                            selectedAnswer = option
                                            recordStudy(card, correct)

                                            if (correct) quizScore++
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = optionColor
                                    )
                                ) {
                                    Text(
                                        option,
                                        modifier = Modifier.padding(16.dp),
                                        color = Ink
                                    )
                                }
                            }

                            if (selectedAnswer == "__TIMEOUT__") {
                                Text("Time is up!")
                            }

                            if (selectedAnswer != null &&
                                selectedAnswer != "__TIMEOUT__"
                            ) {
                                Text(
                                    if (selectedAnswer == card.answer)
                                        "Correct! ✓"
                                    else
                                        "Correct answer: ${card.answer}"
                                )

                                Button(onClick = { nextQuizQuestion() }) {
                                    Text(
                                        if (quizIndex < quizPool.lastIndex)
                                            "Next question"
                                        else
                                            "Finish quiz"
                                    )
                                }
                            }
                        }
                    }

                    if (quizFinished) {
                        Text(
                            "Quiz completed! 🏆",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text("Score: $quizScore / ${quizPool.size}")

                        val percent = if (quizPool.isNotEmpty()) {
                            quizScore * 100 / quizPool.size
                        } else 0

                        Text("Accuracy: $percent%")

                        Button(onClick = { startQuiz() }) {
                            Text("Try again")
                        }

                        OutlinedButton(onClick = {
                            quizFinished = false
                            page = "Stats"
                        }) {
                            Text("View statistics")
                        }
                    }

                    Text(
                        "Leaderboard",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val scores = leaderboard.split(",").mapNotNull {
                        it.toIntOrNull()
                    }

                    if (scores.isEmpty()) {
                        Text("Complete a quiz to record your first score.")
                    } else {
                        scores.forEachIndexed { i, score ->
                            Text("${i + 1}. $score%")
                        }
                    }
                }

                // STATISTICS AND WEAK TOPICS
                "Stats" -> {
                    Text(
                        "Your Progress 📊",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val correct = stats.values.sumOf { it.first }
                    val total = stats.values.sumOf { it.second }
                    val accuracy = if (total > 0) correct * 100 / total else 0

                    DashboardTile("Study days", dates.size.toString(), Mint)
                    DashboardTile("Correct answers", correct.toString(), Lavender)
                    DashboardTile("Overall accuracy", "$accuracy%", Peach)

                    Text(
                        "Weak-topic analysis",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (stats.isEmpty()) {
                        Text("Study cards or take a quiz to see topic results.")
                    } else {
                        val topics = stats.entries.sortedBy {
                            if (it.value.second == 0) 1.0
                            else it.value.first.toDouble() / it.value.second
                        }

                        topics.forEach { entry ->
                            val right = entry.value.first
                            val totalAnswers = entry.value.second
                            val percent = if (totalAnswers > 0) {
                                right * 100 / totalAnswers
                            } else 0

                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(15.dp)) {
                                    Text(entry.key, fontWeight = FontWeight.Bold)
                                    Text("$percent% accuracy ($right/$totalAnswers)")
                                    LinearProgressIndicator(
                                        progress = percent / 100f,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (percent < 60) {
                                        Text(
                                            "Priority: revise this topic",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "Study calendar",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    StudyCalendar(dates)
                }

                // SETTINGS
                "Settings" -> {
                    Text(
                        "Settings ⚙",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Dark mode", fontWeight = FontWeight.Bold)
                                Text("Switch between white and dark themes")
                            }

                            Switch(
                                checked = darkMode,
                                onCheckedChange = onDarkModeChange
                            )
                        }
                    }

                    Text("Daily study goal: $goal cards")

                    Slider(
                        value = goal.toFloat(),
                        onValueChange = {
                            goal = it.toInt().coerceAtLeast(1)
                            prefs.edit().putInt("goal", goal).apply()
                        },
                        valueRange = 1f..50f
                    )

                    Button(
                        onClick = {
                            exportLauncher.launch("studyspace_flashcards.json")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export flashcards (JSON)")
                    }

                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/*")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import flashcards (JSON)")
                    }

                    Text("Your flashcards and statistics are stored locally.")
                }
            }
        }
    }

    // ADD FLASHCARD DIALOG
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Flashcard") },
            text = {
                Column {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("Question") }
                    )

                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text("Answer") }
                    )

                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") }
                    )

                    Text("Difficulty")

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Easy", "Medium", "Hard").forEach {
                            FilterChip(
                                selected = difficulty == it,
                                onClick = { difficulty = it },
                                label = { Text(it) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (question.isNotBlank() && answer.isNotBlank()) {
                        val id = (cards.maxOfOrNull { it.id } ?: 0) + 1

                        cards = cards + Flashcard(
                            id = id,
                            question = question.trim(),
                            answer = answer.trim(),
                            category = category.trim().ifBlank { "General" },
                            difficulty = difficulty
                        )

                        saveCards(context, cards)

                        question = ""
                        answer = ""
                        category = "General"
                        difficulty = "Medium"
                        showAddDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (message.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { message = "" },
            title = { Text("StudySpace") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { message = "" }) {
                    Text("OK")
                }
            }
        )
    }
}

// --------------------------------------------------
// DASHBOARD TILE
// --------------------------------------------------

@Composable
fun DashboardTile(title: String, value: String, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                value,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

// --------------------------------------------------
// STUDY CALENDAR
// --------------------------------------------------

@Composable
fun StudyCalendar(dates: Set<String>) {
    val format = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
    val now = remember { Date() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        for (offset in 6 downTo 0) {
            val date = java.util.Calendar.getInstance().apply {
                time = now
                add(java.util.Calendar.DAY_OF_YEAR, -offset)
            }.time

            val key = format.format(date)
            val label = SimpleDateFormat("EEE", Locale.US).format(date)
            val done = key in dates

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            if (done) Mint else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (done) "✓" else "·",
                        color = if (done) Ink
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(label, fontSize = 10.sp)
            }
        }
    }
}

// --------------------------------------------------
// JSON STORAGE AND MIGRATION
// --------------------------------------------------

fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun cardsToJson(cards: List<Flashcard>): JSONArray {
    val array = JSONArray()

    cards.forEach { card ->
        array.put(
            JSONObject()
                .put("id", card.id)
                .put("question", card.question)
                .put("answer", card.answer)
                .put("category", card.category)
                .put("favorite", card.favorite)
                .put("difficulty", card.difficulty)
        )
    }

    return array
}

fun parseCards(json: String): List<Flashcard> {
    val array = JSONArray(json)

    return (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)

        Flashcard(
            id = obj.optInt("id", i + 1),
            question = obj.optString("question"),
            answer = obj.optString("answer"),
            category = obj.optString("category", "General"),
            favorite = obj.optBoolean("favorite", false),
            difficulty = obj.optString("difficulty", "Medium")
        )
    }
}

fun loadCards(context: Context): List<Flashcard> {
    val newPrefs = context.getSharedPreferences(
        "studyspace", Context.MODE_PRIVATE
    )

    val saved = newPrefs.getString("cards", null)

    if (saved != null) {
        return try {
            parseCards(saved)
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Read the earlier Flashcard Quiz App storage if present.
    val oldPrefs = context.getSharedPreferences(
        "flashcard_preferences", Context.MODE_PRIVATE
    )

    val oldJson = oldPrefs.getString("flashcards", null)

    if (oldJson != null) {
        try {
            val migrated = parseCards(oldJson)
            saveCards(context, migrated)
            return migrated
        } catch (_: Exception) {
            // Fall back to sample cards if the old JSON differs.
        }
    }

    val starter = listOf(
        Flashcard(1, "What is AI?", "Artificial Intelligence", "AI"),
        Flashcard(2, "What is ML?", "Machine Learning", "AI"),
        Flashcard(3, "What is Python?", "A programming language", "Programming"),
        Flashcard(4, "What is a database?", "An organized collection of data", "DBMS")
    )

    saveCards(context, starter)
    return starter
}

fun saveCards(context: Context, cards: List<Flashcard>) {
    context.getSharedPreferences("studyspace", Context.MODE_PRIVATE)
        .edit()
        .putString("cards", cardsToJson(cards).toString())
        .apply()
}

fun loadStats(context: Context): Map<String, Pair<Int, Int>> {
    val json = context.getSharedPreferences(
        "studyspace", Context.MODE_PRIVATE
    ).getString("stats", null) ?: return emptyMap()

    return try {
        val obj = JSONObject(json)
        val result = mutableMapOf<String, Pair<Int, Int>>()
        val keys = obj.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val item = obj.getJSONObject(key)
            result[key] = item.optInt("correct") to item.optInt("total")
        }

        result
    } catch (_: Exception) {
        emptyMap()
    }
}

fun saveStats(context: Context, stats: Map<String, Pair<Int, Int>>) {
    val obj = JSONObject()

    stats.forEach { (category, result) ->
        obj.put(
            category,
            JSONObject()
                .put("correct", result.first)
                .put("total", result.second)
        )
    }

    context.getSharedPreferences("studyspace", Context.MODE_PRIVATE)
        .edit()
        .putString("stats", obj.toString())
        .apply()
}
