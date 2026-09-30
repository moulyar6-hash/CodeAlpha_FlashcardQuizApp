package com.example.flashcardquiz

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


// -----------------------------
// Flashcard data
// -----------------------------

data class Flashcard(
    val question: String,
    val answer: String
)


// -----------------------------
// Save flashcards
// -----------------------------

fun saveFlashcards(
    context: Context,
    flashcards: List<Flashcard>
) {
    val preferences = context.getSharedPreferences(
        "flashcard_preferences",
        Context.MODE_PRIVATE
    )

    val jsonArray = org.json.JSONArray()

    flashcards.forEach { flashcard ->

        val jsonObject = org.json.JSONObject()

        jsonObject.put(
            "question",
            flashcard.question
        )

        jsonObject.put(
            "answer",
            flashcard.answer
        )

        jsonArray.put(jsonObject)
    }

    preferences.edit()
        .putString(
            "flashcards",
            jsonArray.toString()
        )
        .apply()
}


// -----------------------------
// Load flashcards
// -----------------------------

fun loadFlashcards(
    context: Context
): List<Flashcard> {

    val preferences = context.getSharedPreferences(
        "flashcard_preferences",
        Context.MODE_PRIVATE
    )

    val savedData = preferences.getString(
        "flashcards",
        null
    )

    // First time opening the app
    if (savedData == null) {

        return listOf(

            Flashcard(
                "What is Artificial Intelligence?",
                "AI is the simulation of human intelligence in machines."
            ),

            Flashcard(
                "What is Machine Learning?",
                "Machine Learning allows computers to learn from data."
            ),

            Flashcard(
                "What is Python?",
                "Python is a popular high-level programming language."
            )
        )
    }

    val jsonArray = org.json.JSONArray(savedData)

    val flashcards = mutableListOf<Flashcard>()

    for (i in 0 until jsonArray.length()) {

        val jsonObject = jsonArray.getJSONObject(i)

        flashcards.add(
            Flashcard(
                jsonObject.getString("question"),
                jsonObject.getString("answer")
            )
        )
    }

    return flashcards
}


// -----------------------------
// Main Activity
// -----------------------------

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContent {

            MaterialTheme {

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {

                    FlashcardApp()
                }
            }
        }
    }
}


// -----------------------------
// Main Flashcard App
// -----------------------------

@Composable
fun FlashcardApp() {

    val context = LocalContext.current

    // Load saved flashcards
    val flashcards = remember {

        mutableStateListOf<Flashcard>().apply {

            addAll(
                loadFlashcards(context)
            )
        }
    }

    var currentIndex by remember {
        mutableStateOf(0)
    }

    var showAnswer by remember {
        mutableStateOf(false)
    }

    var showAddDialog by remember {
        mutableStateOf(false)
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }


    // -----------------------------
    // Main screen
    // -----------------------------

    Column(

        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),

        horizontalAlignment = Alignment.CenterHorizontally

    ) {

        // App title

        Text(

            text = "📚 Flashcard Quiz",

            fontSize = 30.sp,

            fontWeight = FontWeight.Bold,

            color = MaterialTheme.colorScheme.primary
        )


        Spacer(
            modifier = Modifier.height(8.dp)
        )


        // Subtitle

        Text(

            text = "Study smarter, one card at a time!",

            fontSize = 16.sp,

            color = MaterialTheme.colorScheme.onSurfaceVariant
        )


        Spacer(
            modifier = Modifier.height(25.dp)
        )


        // -----------------------------
        // Flashcard section
        // -----------------------------

        if (flashcards.isNotEmpty()) {

            // Card counter

            Text(

                text = "Card ${currentIndex + 1} of ${flashcards.size}",

                fontSize = 16.sp,

                fontWeight = FontWeight.Bold,

                color = MaterialTheme.colorScheme.primary
            )


            Spacer(
                modifier = Modifier.height(15.dp)
            )


            // Flashcard

            Card(

                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),

                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    20.dp
                )

            ) {

                Column(

                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),

                    verticalArrangement = Arrangement.Center,

                    horizontalAlignment = Alignment.CenterHorizontally

                ) {

                    // QUESTION / ANSWER label

                    Text(

                        text = if (showAnswer)
                            "ANSWER"
                        else
                            "QUESTION",

                        fontSize = 14.sp,

                        fontWeight = FontWeight.Bold,

                        color = MaterialTheme.colorScheme.primary
                    )


                    Spacer(
                        modifier = Modifier.height(15.dp)
                    )


                    // Question or answer

                    Text(

                        text = if (showAnswer)
                            flashcards[currentIndex].answer
                        else
                            flashcards[currentIndex].question,

                        fontSize = 22.sp,

                        lineHeight = 30.sp,

                        fontWeight =
                            if (showAnswer)
                                FontWeight.Normal
                            else
                                FontWeight.Bold,

                        textAlign = TextAlign.Center
                    )
                }
            }


            Spacer(
                modifier = Modifier.height(20.dp)
            )


            // -----------------------------
            // Show Answer button
            // -----------------------------

            Button(

                onClick = {

                    showAnswer = !showAnswer
                },

                modifier = Modifier.fillMaxWidth(),

                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    12.dp
                )

            ) {

                Text(

                    text = if (showAnswer)
                        "Hide Answer"
                    else
                        "Show Answer",

                    fontSize = 16.sp,

                    fontWeight = FontWeight.Bold
                )
            }


            Spacer(
                modifier = Modifier.height(12.dp)
            )


            // -----------------------------
            // Previous / Next
            // -----------------------------

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceEvenly

            ) {

                // Previous

                OutlinedButton(

                    onClick = {

                        if (currentIndex > 0) {

                            currentIndex--

                            showAnswer = false
                        }
                    },

                    enabled = currentIndex > 0,

                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        12.dp
                    )

                ) {

                    Text("Previous")
                }


                // Next

                OutlinedButton(

                    onClick = {

                        if (
                            currentIndex <
                            flashcards.size - 1
                        ) {

                            currentIndex++

                            showAnswer = false
                        }
                    },

                    enabled =
                        currentIndex <
                                flashcards.size - 1,

                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        12.dp
                    )

                ) {

                    Text("Next")
                }
            }


            Spacer(
                modifier = Modifier.height(15.dp)
            )


            // -----------------------------
            // Edit / Delete
            // -----------------------------

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceEvenly

            ) {

                // Edit

                Button(

                    onClick = {

                        showEditDialog = true
                    },

                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        12.dp
                    )

                ) {

                    Text(

                        text = "Edit",

                        fontWeight = FontWeight.Bold
                    )
                }


                // Delete

                Button(

                    onClick = {

                        showDeleteDialog = true
                    },

                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        12.dp
                    )

                ) {

                    Text(

                        text = "Delete",

                        fontWeight = FontWeight.Bold
                    )
                }
            }


            Spacer(
                modifier = Modifier.height(15.dp)
            )
        }


        // -----------------------------
        // Add new flashcard
        // -----------------------------

        Button(

            onClick = {

                showAddDialog = true
            },

            modifier = Modifier.fillMaxWidth(),

            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                12.dp
            )

        ) {

            Text(

                text = "Add New Flashcard",

                fontWeight = FontWeight.Bold
            )
        }
    }


    // ============================================================
    // ADD FLASHCARD DIALOG
    // ============================================================

    if (showAddDialog) {

        FlashcardDialog(

            title = "Add Flashcard",

            initialQuestion = "",

            initialAnswer = "",

            onDismiss = {

                showAddDialog = false
            },

            onSave = { question, answer ->

                flashcards.add(

                    Flashcard(
                        question,
                        answer
                    )
                )

                saveFlashcards(
                    context,
                    flashcards
                )

                currentIndex =
                    flashcards.size - 1

                showAnswer = false

                showAddDialog = false
            }
        )
    }


    // ============================================================
    // EDIT FLASHCARD DIALOG
    // ============================================================

    if (
        showEditDialog &&
        flashcards.isNotEmpty()
    ) {

        FlashcardDialog(

            title = "Edit Flashcard",

            initialQuestion =
                flashcards[currentIndex].question,

            initialAnswer =
                flashcards[currentIndex].answer,

            onDismiss = {

                showEditDialog = false
            },

            onSave = { question, answer ->

                flashcards[currentIndex] =
                    Flashcard(
                        question,
                        answer
                    )

                saveFlashcards(
                    context,
                    flashcards
                )

                showAnswer = false

                showEditDialog = false
            }
        )
    }


    // ============================================================
    // DELETE CONFIRMATION DIALOG
    // ============================================================

    if (
        showDeleteDialog &&
        flashcards.isNotEmpty()
    ) {

        AlertDialog(

            onDismissRequest = {

                showDeleteDialog = false
            },

            title = {

                Text("Delete Flashcard?")
            },

            text = {

                Text(
                    "Are you sure you want to delete this flashcard?"
                )
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        flashcards.removeAt(
                            currentIndex
                        )

                        saveFlashcards(
                            context,
                            flashcards
                        )

                        if (flashcards.isEmpty()) {

                            currentIndex = 0

                        } else if (
                            currentIndex >=
                            flashcards.size
                        ) {

                            currentIndex =
                                flashcards.size - 1
                        }

                        showAnswer = false

                        showDeleteDialog = false
                    }

                ) {

                    Text("Delete")
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {

                        showDeleteDialog = false
                    }

                ) {

                    Text("Cancel")
                }
            }
        )
    }
}


// ============================================================
// FLASHCARD ADD / EDIT DIALOG
// ============================================================

@Composable
fun FlashcardDialog(

    title: String,

    initialQuestion: String,

    initialAnswer: String,

    onDismiss: () -> Unit,

    onSave: (String, String) -> Unit

) {

    var question by remember {

        mutableStateOf(
            initialQuestion
        )
    }

    var answer by remember {

        mutableStateOf(
            initialAnswer
        )
    }


    AlertDialog(

        onDismissRequest = onDismiss,

        title = {

            Text(title)
        },

        text = {

            Column {

                // Question

                OutlinedTextField(

                    value = question,

                    onValueChange = {

                        question = it
                    },

                    label = {

                        Text("Question")
                    },

                    modifier = Modifier.fillMaxWidth()
                )


                Spacer(
                    modifier = Modifier.height(12.dp)
                )


                // Answer

                OutlinedTextField(

                    value = answer,

                    onValueChange = {

                        answer = it
                    },

                    label = {

                        Text("Answer")
                    },

                    modifier = Modifier.fillMaxWidth()
                )
            }
        },

        confirmButton = {

            TextButton(

                onClick = {

                    if (
                        question.isNotBlank() &&
                        answer.isNotBlank()
                    ) {

                        onSave(

                            question.trim(),

                            answer.trim()
                        )
                    }
                }

            ) {

                Text("Save")
            }
        },

        dismissButton = {

            TextButton(

                onClick = onDismiss

            ) {

                Text("Cancel")
            }
        }
    )
}